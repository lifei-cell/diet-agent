package com.diet.service.intent;

import com.diet.enums.Intent;
import com.diet.model.SlotBundle;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 模型漏抽取明确槽位时的保守规则兜底。
 *
 * <p>仅填充当前槽位字典中存在的值，且绝不覆盖模型已识别出的非空结果；它解决的是
 * “中午吃什么、减脂高蛋白”被模型错误判成空槽位澄清的问题，不替代 LLM 的开放语义理解。</p>
 */
@Service
public class IntentSlotFallbackService {

    public SlotBundle mergeExplicitSignals(String userInput, SlotBundle modelSlots, Map<String, List<String>> options) {
        SlotBundle safeModelSlots = modelSlots == null ? SlotBundle.empty() : modelSlots;
        String text = userInput == null ? "" : userInput.replaceAll("\\s+", "");
        return new SlotBundle(
                fill(safeModelSlots.mealTime(), mealTimes(text, options)),
                safeModelSlots.mood(),
                safeModelSlots.scene(),
                fill(safeModelSlots.healthGoal(), healthGoals(text, options)),
                safeModelSlots.cuisine(),
                fill(safeModelSlots.taste(), tastes(text, options)),
                safeModelSlots.convenience()
        );
    }

    /** 明确餐次加健康/饮食偏好时，不允许模型错误地继续进入 CLARIFY。 */
    public Intent reviseClarifyIntent(Intent modelIntent, SlotBundle slots) {
        if (modelIntent != Intent.CLARIFY_NEEDED || slots == null || slots.mealTime().isEmpty()) {
            return modelIntent;
        }
        boolean hasPreference = !slots.healthGoal().isEmpty()
                || !slots.cuisine().isEmpty()
                || !slots.taste().isEmpty()
                || !slots.scene().isEmpty()
                || !slots.convenience().isEmpty();
        return hasPreference ? Intent.MEAL_RECOMMENDATION : modelIntent;
    }

    private List<String> mealTimes(String text, Map<String, List<String>> options) {
        List<String> values = new ArrayList<>();
        if (contains(text, "早餐", "早饭", "早上")) {
            addIfAllowed(values, options, "mealTime", "早餐");
        }
        if (contains(text, "午餐", "午饭", "中午", "中餐")) {
            addIfAllowed(values, options, "mealTime", "午餐");
        }
        if (contains(text, "晚餐", "晚饭", "晚上")) {
            addIfAllowed(values, options, "mealTime", "晚餐");
        }
        return List.copyOf(values);
    }

    private List<String> healthGoals(String text, Map<String, List<String>> options) {
        List<String> values = new ArrayList<>();
        if (contains(text, "减脂", "减肥", "减重", "瘦身", "低脂", "低油")) {
            addIfAllowed(values, options, "healthGoal", "减脂");
        }
        if (contains(text, "高蛋白", "补充蛋白", "增肌")) {
            addIfAllowed(values, options, "healthGoal", "高蛋白");
        }
        if (text.contains("清淡")) {
            addIfAllowed(values, options, "healthGoal", "清淡");
        }
        return List.copyOf(values);
    }

    private List<String> tastes(String text, Map<String, List<String>> options) {
        List<String> values = new ArrayList<>();
        if (text.contains("清淡")) {
            addIfAllowed(values, options, "taste", "清淡");
        }
        if (text.contains("麻辣")) {
            addIfAllowed(values, options, "taste", "麻辣");
        } else if (text.contains("辣")) {
            addIfAllowed(values, options, "taste", "辣");
        }
        return List.copyOf(values);
    }

    private List<String> fill(List<String> modelValues, List<String> fallbackValues) {
        return modelValues == null || modelValues.isEmpty() ? fallbackValues : modelValues;
    }

    private void addIfAllowed(List<String> target, Map<String, List<String>> options, String slotName, String value) {
        if (options.getOrDefault(slotName, List.of()).contains(value)) {
            target.add(value);
        }
    }

    private boolean contains(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }
}
