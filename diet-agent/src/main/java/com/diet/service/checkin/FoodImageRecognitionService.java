package com.diet.service.checkin;

import com.diet.model.FoodRecognitionItem;
import com.diet.service.llm.LlmCallResilience;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Provider-isolated multimodal recognizer. A missing key or a provider error returns an empty
 * editable draft instead of fabricating a nutritional estimate.
 */
@Service
public class FoodImageRecognitionService {
    private static final Logger log = LoggerFactory.getLogger(FoodImageRecognitionService.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String visionModel;
    private final String apiUrl;
    private final LlmCallResilience llmCallResilience;

    @Autowired
    public FoodImageRecognitionService(
            ObjectMapper objectMapper,
            @Value("${agentscope.dashscope.api-key:}") String apiKey,
            @Value("${diet.vision.model:qwen3.7-plus}") String visionModel,
            @Value("${diet.vision.api-url:https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions}") String apiUrl,
            LlmCallResilience llmCallResilience,
            @Value("${diet.llm.resilience.vision-connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${diet.llm.resilience.vision-read-timeout-ms:10000}") int readTimeoutMs
    ) {
        this(buildRestClient(connectTimeoutMs, readTimeoutMs), objectMapper, apiKey, visionModel, apiUrl, llmCallResilience);
    }

    /** Keeps direct construction in lightweight tests and tools possible. */
    public FoodImageRecognitionService(ObjectMapper objectMapper, String apiKey, String visionModel, String apiUrl) {
        this(RestClient.create(), objectMapper, apiKey, visionModel, apiUrl, new LlmCallResilience());
    }

    FoodImageRecognitionService(
            RestClient restClient,
            ObjectMapper objectMapper,
            String apiKey,
            String visionModel,
            String apiUrl,
            LlmCallResilience llmCallResilience
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.visionModel = visionModel;
        this.apiUrl = apiUrl;
        this.llmCallResilience = llmCallResilience;
    }

    public RecognitionResult recognize(byte[] imageData, String mediaType) {
        if (apiKey == null || apiKey.isBlank()) {
            return RecognitionResult.manual("图片已保存为待确认草稿。视觉识别服务尚未配置，请手动补充菜品和营养数据。");
        }
        try {
            LlmCallResilience.Execution<String> execution = llmCallResilience.execute("food-vision", () -> restClient.post()
                    .uri(apiUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(buildRequest(imageData, mediaType))
                    .retrieve()
                    .body(String.class));
            String response = execution.value();
            List<FoodRecognitionItem> items = parseItems(response);
            if (items.isEmpty()) {
                return RecognitionResult.manual("未能从图片中可靠识别菜品，请手动补充后再保存打卡。");
            }
            return new RecognitionResult(true,
                    "已识别出图片中的菜品和估算营养，请核对份量、油盐和酱料后再确认打卡。", items);
        } catch (Exception exception) {
            if (exception instanceof LlmCallResilience.LlmCallException llmError) {
                log.warn("Food image recognition degraded: channel={}, attempts={}, circuitOpen={}",
                        llmError.channel(), llmError.attempts(), llmError.circuitOpen());
            } else {
                log.warn("Food image recognition is unavailable: {}", exception.getMessage());
            }
            return RecognitionResult.manual("自动识别暂不可用，图片已保存为待确认草稿；你可以手动填写后完成打卡。");
        }
    }

    private static RestClient buildRestClient(int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.max(500, connectTimeoutMs));
        requestFactory.setReadTimeout(Math.max(1_000, readTimeoutMs));
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    private Map<String, Object> buildRequest(byte[] imageData, String mediaType) {
        String dataUrl = "data:" + mediaType + ";base64," + Base64.getEncoder().encodeToString(imageData);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", visionModel);
        request.put("temperature", 0.1);
        request.put("messages", List.of(
                Map.of("role", "system", "content", "你是餐食图片识别助手。只能根据图片估算，不能提供医疗诊断或疗效承诺。"),
                Map.of("role", "user", "content", List.of(
                        Map.of("type", "image_url", "image_url", Map.of("url", dataUrl)),
                        Map.of("type", "text", "text", "识别图片中的可食用菜品并估算每项份量和营养。只返回 JSON：{\"items\":[{\"name\":string,\"estimatedWeightG\":number,\"energyKcal\":number,\"proteinG\":number,\"fatG\":number,\"carbohydrateG\":number,\"confidence\":0到1}]}; 无法可靠判断的项不要编造。")
                ))
        ));
        return request;
    }

    private List<FoodRecognitionItem> parseItems(String providerResponse) throws Exception {
        JsonNode root = objectMapper.readTree(providerResponse);
        String content = root.path("choices").path(0).path("message").path("content").asText();
        JsonNode result = objectMapper.readTree(extractJson(content));
        List<FoodRecognitionItem> items = new ArrayList<>();
        for (JsonNode item : result.path("items")) {
            String name = item.path("name").asText("").trim();
            if (name.isBlank()) {
                continue;
            }
            items.add(new FoodRecognitionItem(
                    name,
                    positiveOrNull(item, "estimatedWeightG"),
                    positiveOrNull(item, "energyKcal"),
                    positiveOrNull(item, "proteinG"),
                    positiveOrNull(item, "fatG"),
                    positiveOrNull(item, "carbohydrateG"),
                    confidence(item.path("confidence").asDouble(0)),
                    "VISION_ESTIMATE"
            ));
        }
        return items.stream().limit(12).toList();
    }

    private String extractJson(String text) {
        String candidate = text == null ? "" : text.trim();
        if (candidate.startsWith("```")) {
            int newline = candidate.indexOf('\n');
            candidate = newline >= 0 ? candidate.substring(newline + 1) : candidate;
            int endFence = candidate.lastIndexOf("```");
            if (endFence >= 0) {
                candidate = candidate.substring(0, endFence);
            }
        }
        int begin = candidate.indexOf('{');
        int end = candidate.lastIndexOf('}');
        if (begin < 0 || end < begin) {
            throw new IllegalArgumentException("Vision response does not contain JSON");
        }
        return candidate.substring(begin, end + 1);
    }

    private Double positiveOrNull(JsonNode item, String field) {
        if (!item.has(field) || !item.get(field).isNumber()) {
            return null;
        }
        double value = item.get(field).asDouble();
        return Double.isFinite(value) && value >= 0 ? value : null;
    }

    private Double confidence(double value) {
        return Math.max(0, Math.min(1, value));
    }

    public record RecognitionResult(boolean automated, String message, List<FoodRecognitionItem> items) {
        static RecognitionResult manual(String message) {
            return new RecognitionResult(false, message, List.of());
        }
    }
}
