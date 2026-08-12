package com.diet.service.llm;

import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmCallResilienceTest {

    @Test
    void retriesTransientFailureAndReturnsRecoveredResult() {
        LlmCallResilience resilience = new LlmCallResilience(2, 0, 0, 3, 10_000, 0);
        AtomicInteger calls = new AtomicInteger();

        LlmCallResilience.Execution<String> result = resilience.execute("text-agent", () -> {
            if (calls.incrementAndGet() == 1) {
                throw new SocketTimeoutException("provider timed out");
            }
            return "ok";
        });

        assertEquals("ok", result.value());
        assertEquals(2, result.attempts());
        assertEquals(2, calls.get());
    }

    @Test
    void opensCircuitAfterRepeatedAvailabilityFailures() {
        LlmCallResilience resilience = new LlmCallResilience(1, 0, 0, 2, 10_000, 0);
        AtomicInteger calls = new AtomicInteger();

        for (int ignored = 0; ignored < 2; ignored++) {
            assertThrows(LlmCallResilience.LlmCallException.class, () -> resilience.execute("food-vision", () -> {
                calls.incrementAndGet();
                throw new SocketTimeoutException("provider timed out");
            }));
        }

        LlmCallResilience.LlmCallException error = assertThrows(
                LlmCallResilience.LlmCallException.class,
                () -> resilience.execute("food-vision", () -> "should not execute")
        );
        assertTrue(error.circuitOpen());
        assertEquals(2, calls.get());
    }
}
