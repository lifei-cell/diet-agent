package com.diet.service.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;

/**
 * Guards calls to external LLM providers with a small, dependency-free resilience policy.
 *
 * <p>It retries only availability failures, applies capped backoff with jitter, and opens a
 * circuit after repeated unavailable calls. Callers still own their business fallback, so a
 * model outage never turns into a generic request failure.</p>
 */
@Component
public class LlmCallResilience {

    private static final Logger log = LoggerFactory.getLogger(LlmCallResilience.class);

    private final int maxAttempts;
    private final long initialBackoffMs;
    private final long maxBackoffMs;
    private final int circuitFailureThreshold;
    private final long circuitOpenMs;
    private final long jitterMs;
    private final Map<String, CircuitState> circuits = new ConcurrentHashMap<>();

    /** Defaults are intentionally conservative for direct unit-test construction. */
    public LlmCallResilience() {
        this(2, 250, 1_000, 5, 30_000, 100);
    }

    @Autowired
    public LlmCallResilience(
            @Value("${diet.llm.resilience.max-attempts:2}") int maxAttempts,
            @Value("${diet.llm.resilience.initial-backoff-ms:250}") long initialBackoffMs,
            @Value("${diet.llm.resilience.max-backoff-ms:1000}") long maxBackoffMs,
            @Value("${diet.llm.resilience.circuit-failure-threshold:5}") int circuitFailureThreshold,
            @Value("${diet.llm.resilience.circuit-open-ms:30000}") long circuitOpenMs,
            @Value("${diet.llm.resilience.jitter-ms:100}") long jitterMs
    ) {
        this.maxAttempts = Math.max(1, Math.min(3, maxAttempts));
        this.initialBackoffMs = Math.max(0, initialBackoffMs);
        this.maxBackoffMs = Math.max(this.initialBackoffMs, maxBackoffMs);
        this.circuitFailureThreshold = Math.max(1, circuitFailureThreshold);
        this.circuitOpenMs = Math.max(1, circuitOpenMs);
        this.jitterMs = Math.max(0, jitterMs);
    }

    /**
     * Executes one provider operation. A channel represents one independently degradable
     * dependency, for example {@code text-agent} or {@code food-vision}.
     */
    public <T> Execution<T> execute(String channel, CheckedSupplier<T> invocation) {
        String safeChannel = channel == null || channel.isBlank() ? "default" : channel;
        CircuitState circuit = circuits.computeIfAbsent(safeChannel, ignored -> new CircuitState());
        circuit.beforeCall(safeChannel);

        Throwable lastFailure = null;
        boolean availabilityFailure = false;
        int attemptsMade = 0;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            attemptsMade = attempt;
            try {
                T value = invocation.get();
                if (value == null) {
                    throw new IllegalStateException("LLM provider returned an empty response");
                }
                circuit.onSuccess();
                if (attempt > 1) {
                    log.info("LLM call recovered after retry: channel={}, attempts={}", safeChannel, attempt);
                }
                return new Execution<>(value, attempt);
            } catch (Exception error) {
                lastFailure = error;
                availabilityFailure = isAvailabilityFailure(error);
                boolean shouldRetry = availabilityFailure && attempt < maxAttempts;
                if (!shouldRetry) {
                    break;
                }
                long delayMs = backoffMs(attempt);
                log.warn("LLM call failed; retrying: channel={}, attempt={}/{}, delayMs={}, cause={}",
                        safeChannel, attempt, maxAttempts, delayMs, conciseMessage(error));
                sleep(delayMs, safeChannel, attempt, error);
            }
        }

        if (availabilityFailure) {
            circuit.onAvailabilityFailure(circuitFailureThreshold, circuitOpenMs);
        } else {
            // A half-open probe that failed for a non-availability reason must not keep the
            // circuit permanently locked in probe mode.
            circuit.onNonAvailabilityFailure();
        }
        throw new LlmCallException(safeChannel, attemptsMade, false, lastFailure);
    }

    private long backoffMs(int failedAttempt) {
        long exponential;
        try {
            exponential = Math.multiplyExact(initialBackoffMs, 1L << Math.max(0, failedAttempt - 1));
        } catch (ArithmeticException ignored) {
            exponential = maxBackoffMs;
        }
        long capped = Math.min(maxBackoffMs, exponential);
        return capped + (jitterMs == 0 ? 0 : ThreadLocalRandom.current().nextLong(jitterMs + 1));
    }

    private void sleep(long delayMs, String channel, int attempt, Exception failure) {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new LlmCallException(channel, attempt, false, failure);
        }
    }

    /** Do not retry invalid requests or authentication errors; those cannot self-heal. */
    private boolean isAvailabilityFailure(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof SocketTimeoutException
                    || current instanceof TimeoutException
                    || current instanceof ConnectException
                    || current instanceof IOException
                    || current instanceof ResourceAccessException) {
                return true;
            }
            if (current instanceof RestClientResponseException responseException) {
                int status = responseException.getStatusCode().value();
                return status == 408 || status == 429 || status >= 500;
            }
        }
        String message = conciseMessage(error).toLowerCase(Locale.ROOT);
        return message.contains("timeout")
                || message.contains("timed out")
                || message.contains("rate limit")
                || message.contains("too many requests")
                || message.contains("temporarily unavailable")
                || message.contains("service unavailable")
                || message.contains("connection reset")
                || message.contains("connection refused")
                || message.contains("connection closed")
                || message.contains(" 429")
                || message.contains(" 502")
                || message.contains(" 503")
                || message.contains(" 504");
    }

    private String conciseMessage(Throwable error) {
        if (error == null) {
            return "unknown";
        }
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    /** Result metadata lets callers add retry information to their own business trace. */
    public record Execution<T>(T value, int attempts) {
    }

    @FunctionalInterface
    public interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    /** Provider failure that callers can log or trace without exposing upstream details to users. */
    public static final class LlmCallException extends RuntimeException {
        private final String channel;
        private final int attempts;
        private final boolean circuitOpen;

        private LlmCallException(String channel, int attempts, boolean circuitOpen, Throwable cause) {
            super(circuitOpen
                    ? "LLM circuit is open for channel " + channel
                    : "LLM call failed for channel " + channel + " after " + attempts + " attempt(s)", cause);
            this.channel = channel;
            this.attempts = attempts;
            this.circuitOpen = circuitOpen;
        }

        public String channel() {
            return channel;
        }

        public int attempts() {
            return attempts;
        }

        public boolean circuitOpen() {
            return circuitOpen;
        }
    }

    private static final class CircuitState {
        private int consecutiveAvailabilityFailures;
        private long openUntilEpochMs;
        private boolean probeInProgress;

        synchronized void beforeCall(String channel) {
            long now = System.currentTimeMillis();
            if (openUntilEpochMs == 0) {
                return;
            }
            if (now < openUntilEpochMs || probeInProgress) {
                throw new LlmCallException(channel, 0, true, null);
            }
            // Half-open: permit a single probe after the cool-down period.
            probeInProgress = true;
        }

        synchronized void onSuccess() {
            consecutiveAvailabilityFailures = 0;
            openUntilEpochMs = 0;
            probeInProgress = false;
        }

        synchronized void onAvailabilityFailure(int threshold, long openDurationMs) {
            consecutiveAvailabilityFailures++;
            probeInProgress = false;
            if (consecutiveAvailabilityFailures >= threshold) {
                openUntilEpochMs = System.currentTimeMillis() + openDurationMs;
            }
        }

        synchronized void onNonAvailabilityFailure() {
            // A provider response such as 400/401 proves the dependency is reachable, even
            // though the caller still needs to fix its request or credentials.
            consecutiveAvailabilityFailures = 0;
            openUntilEpochMs = 0;
            probeInProgress = false;
        }
    }
}
