package com.diet.service.session;

import com.diet.exception.DietException;
import com.diet.exception.SessionConflictException;
import com.diet.mapper.ChatIdempotencyMapper;
import com.diet.model.ChatIdempotencyRow;
import com.diet.model.ChatRequest;
import com.diet.model.ChatResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Makes a client chat request safe to replay. The persisted processing token prevents an old,
 * paused worker from completing a request that a later retry has already reclaimed.
 */
@Service
public class ChatIdempotencyService {
    private static final String PROCESSING = "PROCESSING";
    private static final String COMPLETED = "COMPLETED";

    private final ChatIdempotencyMapper mapper;
    private final ObjectMapper objectMapper;
    private final long processingTimeoutSeconds;

    public ChatIdempotencyService(
            ChatIdempotencyMapper mapper,
            ObjectMapper objectMapper,
            @Value("${diet.session.idempotency-processing-timeout-seconds:90}") long processingTimeoutSeconds
    ) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.processingTimeoutSeconds = Math.max(15, processingTimeoutSeconds);
    }

    /** Returns the previously persisted answer without acquiring the session lock when available. */
    public ChatResponse findCompleted(Long userId, String sessionId, String requestId, String requestHash) {
        ChatIdempotencyRow row = mapper.find(userId, requestId);
        if (row == null) {
            return null;
        }
        verifyRequest(row, sessionId, requestHash);
        return COMPLETED.equals(row.getStatus()) ? parseResponse(row.getResponseJson()) : null;
    }

    /** Claims a new/failed/stale request. A fresh PROCESSING record tells the caller not to repeat work. */
    public StartResult begin(Long userId, String sessionId, String requestId, String requestHash) {
        for (int attempt = 0; attempt < 3; attempt++) {
            ChatIdempotencyRow row = mapper.find(userId, requestId);
            if (row == null) {
                String token = newToken();
                ChatIdempotencyRow created = new ChatIdempotencyRow();
                created.setUserId(userId);
                created.setSessionId(sessionId);
                created.setRequestId(requestId);
                created.setRequestHash(requestHash);
                created.setStatus(PROCESSING);
                created.setProcessingToken(token);
                try {
                    mapper.insert(created);
                    return StartResult.owner(token);
                } catch (DuplicateKeyException ignored) {
                    // A different replica claimed the same request between SELECT and INSERT.
                    continue;
                }
            }
            verifyRequest(row, sessionId, requestHash);
            if (COMPLETED.equals(row.getStatus())) {
                return StartResult.completed(parseResponse(row.getResponseJson()));
            }
            String token = newToken();
            int updated = mapper.takeOver(
                    userId, requestId, requestHash, token,
                    LocalDateTime.now().minusSeconds(processingTimeoutSeconds)
            );
            if (updated == 1) {
                return StartResult.owner(token);
            }
            return StartResult.inProgress();
        }
        return StartResult.inProgress();
    }

    /** Persists the final response so any later retry receives exactly the same result. */
    public ChatResponse complete(
            Long userId,
            String requestId,
            String processingToken,
            String traceId,
            ChatResponse response
    ) {
        String responseJson = toJson(response);
        int updated = mapper.complete(userId, requestId, processingToken, responseJson, traceId);
        if (updated == 1) {
            return response;
        }
        ChatIdempotencyRow latest = mapper.find(userId, requestId);
        if (latest != null && COMPLETED.equals(latest.getStatus())) {
            return parseResponse(latest.getResponseJson());
        }
        throw new SessionConflictException("该聊天请求已被新的重试接管，请使用同一 requestId 重试获取结果");
    }

    /** A failed processing record may be safely reclaimed by the next request with the same id. */
    public void fail(Long userId, String requestId, String processingToken, Exception error) {
        String code = error == null ? "UNKNOWN" : error.getClass().getSimpleName();
        mapper.fail(userId, requestId, processingToken, code.length() > 128 ? code.substring(0, 128) : code);
    }

    /** Deterministically detects accidental reuse of a request id for a different payload. */
    public String requestHash(ChatRequest request) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sessionId", request.sessionId());
            payload.put("message", request.message());
            payload.put("sourceMode", request.sourceMode() == null ? null : request.sourceMode().name());
            payload.put("context", request.context());
            payload.put("nutritionConstraints", request.nutritionConstraints());
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(objectMapper.writeValueAsBytes(payload));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception error) {
            throw new DietException("无法生成聊天请求指纹", error);
        }
    }

    private void verifyRequest(ChatIdempotencyRow row, String sessionId, String requestHash) {
        if (!sessionId.equals(row.getSessionId())) {
            throw new DietException("requestId 已绑定到另一个会话");
        }
        if (!requestHash.equals(row.getRequestHash())) {
            throw new DietException("requestId 不能用于不同的聊天内容");
        }
    }

    private ChatResponse parseResponse(String responseJson) {
        if (responseJson == null || responseJson.isBlank()) {
            throw new DietException("幂等请求缺少可复用的响应");
        }
        try {
            return objectMapper.readValue(responseJson, ChatResponse.class);
        } catch (Exception error) {
            throw new DietException("幂等响应解析失败", error);
        }
    }

    private String toJson(ChatResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception error) {
            throw new DietException("幂等响应序列化失败", error);
        }
    }

    private String newToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public record StartResult(Status status, String processingToken, ChatResponse cachedResponse) {
        public static StartResult owner(String token) {
            return new StartResult(Status.OWNER, token, null);
        }

        public static StartResult completed(ChatResponse response) {
            return new StartResult(Status.COMPLETED, null, response);
        }

        public static StartResult inProgress() {
            return new StartResult(Status.IN_PROGRESS, null, null);
        }
    }

    public enum Status {
        OWNER,
        COMPLETED,
        IN_PROGRESS
    }
}
