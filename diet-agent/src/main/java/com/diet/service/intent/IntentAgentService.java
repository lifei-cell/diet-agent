package com.diet.service.intent;

import com.diet.agent.factory.AgentFactory;
import com.diet.model.ConversationTurn;
import com.diet.enums.Intent;
import com.diet.model.IntentResult;
import com.diet.model.NutritionConstraints;
import com.diet.model.SlotBundle;
import com.diet.service.slot.SlotOptionService;
import com.diet.service.trace.AgentTraceService;
import com.diet.util.LlmJsonService;
import com.diet.util.SlotJsonPicker;
import com.fasterxml.jackson.databind.JsonNode;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

/**
 * IntentAgent 调用服务。
 * 负责调用 LLM 识别 intent + slots，解析 JSON，失败时关键词兜底；不直接写 SessionState。
 */
@Service
public class IntentAgentService {

    /** 按 sessionId 提供 IntentAgent 实例的工厂。 */
    private final AgentFactory agentFactory;

    /** 从 LLM 输出文本中提取 JSON 对象的工具。 */
    private final LlmJsonService llmJsonService;

    /** 槽位字典服务，校验 LLM 输出的标签是否在合法候选值内。 */
    private final SlotOptionService slotOptionService;

    /** 链路追踪服务，callAgent 内部会记录 AGENT_CALL 事件。 */
    private final AgentTraceService agentTraceService;

    /** 模型漏抽取明确餐次/目标时的规则兜底。 */
    private final IntentSlotFallbackService intentSlotFallbackService;

    /** IntentAgent 使用的轻量模型名，来自配置 diet.llm.light-model。 */
    private final String modelName;

    /** 构造器注入全部依赖。 */
    public IntentAgentService(
            AgentFactory agentFactory,
            LlmJsonService llmJsonService,
            SlotOptionService slotOptionService,
            AgentTraceService agentTraceService,
            IntentSlotFallbackService intentSlotFallbackService,
            @Value("${diet.llm.light-model:qwen-turbo}") String modelName
    ) {
        this.agentFactory = agentFactory;
        this.llmJsonService = llmJsonService;
        this.slotOptionService = slotOptionService;
        this.agentTraceService = agentTraceService;
        this.intentSlotFallbackService = intentSlotFallbackService;
        this.modelName = modelName;
    }

    /**
     * 调用 IntentAgent 识别本轮意图和槽位。
     * 由 Orchestrator#handleTurn 调用，返回 IntentResult 供路由和槽位合并。
     */
    public IntentResult recognize(
            String sessionId,
            Long userId,
            String userInput,
            SlotBundle knownSlots,
            NutritionConstraints knownNutritionConstraints,
            List<ConversationTurn> recentHistory
    ) {
        try {
            // 加载全部槽位字段的合法候选值 Map（mealTime/mood/scene 等 → 标签列表） ：把槽位字典传入prompt
            Map<String, List<String>> slotOptions = slotOptionService.findAllOptions();
            
            // 从 AgentFactory 获取当前 session 绑定的 IntentAgent ReActAgent 实例
            ReActAgent agent = agentFactory.get(sessionId).intent();
            // 清空 Agent 内存，避免上一轮对话污染本轮意图识别
            agent.getMemory().clear();
            // 调用 Agent：内部走 agentTraceService.callAgent，记录 AGENT_CALL 事件（含 input/output/latency）
            Msg response = agentTraceService.callAgent(sessionId, "IntentAgent", modelName,
                    agent, buildUserPrompt(userId, sessionId, userInput, knownSlots, knownNutritionConstraints, recentHistory, slotOptions));
            // 解析 Agent 返回的 JSON 文本为 IntentResult（intent + slots + confidence）
            return parseResult(response.getTextContent(), userInput, slotOptions);
        } catch (Exception ignored) {
            // LLM 超时/JSON 解析失败时不抛异常，走关键词 fallback 保证 Orchestrator 可继续
            return fallback(userInput);
        }
    }

    /** 构造传给 IntentAgent 的用户 prompt，包含上下文和输出格式约束。 */
    private String buildUserPrompt(
            Long userId,
            String sessionId,
            String userInput,
            SlotBundle knownSlots,
            NutritionConstraints knownNutritionConstraints,
            List<ConversationTurn> recentHistory,
            Map<String, List<String>> slotOptions
    ) {
        return """
                userId: %s
                sessionId: %s
                recentHistory: %s
                knownSlots: %s
                knownNutritionConstraints: %s
                slotOptions: %s
                当前这一句: %s
                请输出 JSON，字段为 intent、slots、nutritionConstraints、confidence。
                slots 必须从 slotOptions 对应字段的候选值中选择；无法映射则输出 null 或空数组，不要创造标签。
                nutritionConstraints 只提取用户明确表达的数值上限/下限或过敏原排除项；不确定时置 null 或空数组。
                """.formatted(userId, sessionId, recentHistory, knownSlots,
                NutritionConstraints.sanitize(knownNutritionConstraints), slotOptions, userInput);
    }

    /** 将 Agent 返回的 JSON 文本解析为 IntentResult。 */
    private IntentResult parseResult(String content, String userInput, Map<String, List<String>> slotOptions) {
        // 从 LLM 输出中提取 JSON 根节点（可能包裹在 markdown 代码块中）
        JsonNode root = llmJsonService.parseObject(content);

        // 读取 intent 字段并解析为 Intent 枚举，失败时走关键词兜底
        Intent intent = parseIntent(root.path("intent").asText(null), userInput);

        // 若 slots 是嵌套对象则取 slots 节点，否则直接用 root（兼容扁平 JSON）
        JsonNode slotsNode = root.path("slots").isObject() ? root.path("slots") : root;

        // 将 JSON slots 各字段映射为 SlotBundle，并过滤非法字典值
        SlotBundle slots = intentSlotFallbackService.mergeExplicitSignals(
                userInput, parseSlots(slotsNode, slotOptions), slotOptions);
        NutritionConstraints nutritionConstraints = parseNutritionConstraints(root, userInput);

        // 读取 confidence 字段，缺省 0.5
        double confidence = root.path("confidence").asDouble(0.5);

        // 组装并返回 IntentResult
        return new IntentResult(intentSlotFallbackService.reviseClarifyIntent(intent, slots), slots, nutritionConstraints, confidence);
    }

    /** 将 JSON 中的 intent 字符串解析为 Intent 枚举。 */
    private Intent parseIntent(String rawIntent, String userInput) {
        try {
            // rawIntent 为 null 时走关键词兜底；否则 Intent.valueOf 解析
            return rawIntent == null ? fallbackIntent(userInput) : Intent.valueOf(rawIntent);
        } catch (Exception ignored) {
            // 非法枚举名时走关键词兜底
            return fallbackIntent(userInput);
        }
    }

    /** 将 JSON slots 节点各字段转为 SlotBundle，通过 SlotJsonPicker 过滤非法标签。 */
    private SlotBundle parseSlots(JsonNode node, Map<String, List<String>> options) {
        return new SlotBundle(
                SlotJsonPicker.pick(node, "mealTime", options),      // 餐次标签
                SlotJsonPicker.pick(node, "mood", options),          // 心情标签
                SlotJsonPicker.pick(node, "scene", options),         // 场景标签
                SlotJsonPicker.pick(node, "healthGoal", options),    // 健康目标标签
                SlotJsonPicker.pick(node, "cuisine", options),       // 菜系标签
                SlotJsonPicker.pick(node, "taste", options),         // 口味标签
                SlotJsonPicker.pick(node, "convenience", options)    // 便捷性标签
        );
    }

    /** LLM 完全失败时的保守兜底 IntentResult，confidence 固定 0.2。 */
    private IntentResult fallback(String userInput) {
        return new IntentResult(
                fallbackIntent(userInput),
                SlotBundle.empty(),
                fallbackNutritionConstraints(userInput),
                0.2
        );
    }

    /** 关键词规则推断意图，按优先级依次匹配。 */
    private Intent fallbackIntent(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return Intent.CLARIFY_NEEDED;  // 空输入 → 需要澄清
        }
        if (containsAny(userInput, "胃疼", "糖尿病", "孕妇", "未成年人", "治好", "治疗", "极端节食", "绝食")) {
            return Intent.HEALTH_RISK;     // 健康风险关键词
        }
        if (containsAny(userInput, "换一批", "换个", "不要太油", "清淡点", "便宜点", "快一点")) {
            return Intent.MEAL_ADJUST;   // 调整推荐关键词
        }
        if (containsAny(userInput, "三餐", "早中晚", "一周")) {
            return Intent.MEAL_PLAN;     // 多餐规划关键词
        }
        if (containsAny(userInput, "你是谁", "你是 AI", "你好")) {
            return Intent.OTHER;      // 与饮食无关等关键词
        }
        if (containsAny(userInput, "吃什么", "推荐", "晚饭", "午饭", "早餐", "想吃")) {
            return Intent.MEAL_RECOMMENDATION; // 推荐关键词
        }
        return Intent.CLARIFY_NEEDED;    // 默认 → 需要澄清
    }

    /** 判断 text 是否包含 keywords 中任一子串。 */
    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /** 解析 LLM 输出中的 nutritionConstraints，兼容 constraints 别名。 */
    private NutritionConstraints parseNutritionConstraints(JsonNode root, String userInput) {
        JsonNode node = root.path("nutritionConstraints").isObject()
                ? root.path("nutritionConstraints")
                : root.path("constraints");
        NutritionConstraints llmConstraints = new NutritionConstraints(
                nonNegative(node.path("maxEnergyKcal")),
                nonNegative(node.path("minProteinG")),
                nonNegative(node.path("maxFatG")),
                nonNegative(node.path("maxCarbohydrateG")),
                nonNegative(node.path("maxSodiumMg")),
                stringList(node.path("excludedAllergens"))
        );
        return NutritionConstraints.merge(llmConstraints, fallbackNutritionConstraints(userInput));
    }

    /** LLM 不稳定时，识别常见的热量、蛋白质和过敏原表达。 */
    private NutritionConstraints fallbackNutritionConstraints(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return NutritionConstraints.empty();
        }
        String compact = userInput.replaceAll("\\s+", "");
        Double maxEnergy = matchNumber(compact, "(?:不超过|最多|小于等于|≤)(\\d+(?:\\.\\d+)?)(?:kcal|千卡|大卡|卡)");
        Double minProtein = matchNumber(compact, "(?:蛋白质|蛋白)(?:至少|不少于|大于等于|≥)(\\d+(?:\\.\\d+)?)(?:g|克)");
        if (minProtein == null) {
            minProtein = matchNumber(compact, "(?:至少|不少于|大于等于|≥)(\\d+(?:\\.\\d+)?)(?:g|克)(?:蛋白质|蛋白)");
        }
        List<String> allergens = java.util.stream.Stream.of("花生", "乳制品", "鸡蛋", "坚果", "海鲜", "麸质", "大豆")
                .filter(allergen -> compact.contains("不吃" + allergen)
                        || compact.contains("不要" + allergen)
                        || compact.contains("不含" + allergen)
                        || compact.contains("避免" + allergen)
                        || compact.contains(allergen + "过敏"))
                .toList();
        return new NutritionConstraints(maxEnergy, minProtein, null, null, null, allergens);
    }

    private Double nonNegative(JsonNode node) {
        if (node == null || !node.isNumber()) {
            return null;
        }
        double value = node.asDouble();
        return Double.isFinite(value) && value >= 0 ? value : null;
    }

    private List<String> stringList(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (node.isTextual()) {
            return java.util.Arrays.stream(node.asText().split("[,，、]"))
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .toList();
        }
        if (!node.isArray()) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(node.spliterator(), false)
                .filter(JsonNode::isTextual)
                .map(JsonNode::asText)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private Double matchNumber(String text, String regex) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Double.parseDouble(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
