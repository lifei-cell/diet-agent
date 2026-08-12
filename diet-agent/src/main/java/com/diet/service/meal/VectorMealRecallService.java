package com.diet.service.meal;

import com.diet.model.MealSearchRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 可选向量召回适配器。
 *
 * <p>向量库由部署方维护餐食索引；本服务只接收排序后的 ID。返回 ID 后仍要由
 * {@link MealConstraintMatcher} 重新校验权限、营养和过敏原，避免外部索引成为安全边界。</p>
 */
@Service
public class VectorMealRecallService implements VectorMealCandidateRetriever {
    private static final Logger log = LoggerFactory.getLogger(VectorMealRecallService.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String endpoint;
    private final int timeoutFreeLimit;

    @Autowired
    public VectorMealRecallService(
            ObjectMapper objectMapper,
            @Value("${diet.retrieval.vector.enabled:false}") boolean enabled,
            @Value("${diet.retrieval.vector.endpoint:}") String endpoint,
            @Value("${diet.retrieval.vector.top-k:50}") int topK
    ) {
        this(RestClient.create(), objectMapper, enabled, endpoint, topK);
    }

    VectorMealRecallService(RestClient restClient, ObjectMapper objectMapper, boolean enabled, String endpoint, int topK) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.endpoint = endpoint == null ? "" : endpoint.trim();
        this.timeoutFreeLimit = Math.max(1, Math.min(100, topK));
    }

    /** 未配置时静默跳过，保证当前 MySQL-only 部署保持可用。 */
    public List<Long> recall(MealSearchRequest request) {
        if (!enabled || endpoint.isBlank() || request == null || request.queryText() == null || request.queryText().isBlank()) {
            return List.of();
        }
        try {
            String response = restClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody(request))
                    .retrieve()
                    .body(String.class);
            return parseIds(response);
        } catch (Exception error) {
            // 向量召回是扩召回通道，失败时不能阻塞结构化/关键词主链路。
            log.warn("Vector meal recall unavailable; falling back to local channels: {}", error.getMessage());
            return List.of();
        }
    }

    private Map<String, Object> requestBody(MealSearchRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("queryText", request.queryText());
        body.put("sourceMode", request.sourceMode() == null ? null : request.sourceMode().name());
        body.put("userId", request.userId());
        body.put("slots", request.slots());
        body.put("limit", timeoutFreeLimit);
        return body;
    }

    /** 支持 {"ids":[...]} 或 {"data":{"ids":[...]}} 两种轻量协议。 */
    private List<Long> parseIds(String body) throws Exception {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        JsonNode root = objectMapper.readTree(body);
        JsonNode ids = root.path("ids");
        if (!ids.isArray()) {
            ids = root.path("data").path("ids");
        }
        if (!ids.isArray()) {
            return List.of();
        }
        java.util.LinkedHashSet<Long> result = new java.util.LinkedHashSet<>();
        ids.forEach(node -> {
            if (node.isIntegralNumber() && node.asLong() > 0) {
                result.add(node.asLong());
            }
        });
        return result.stream().limit(timeoutFreeLimit).toList();
    }
}
