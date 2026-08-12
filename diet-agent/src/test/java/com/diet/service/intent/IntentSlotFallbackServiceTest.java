package com.diet.service.intent;

import com.diet.enums.Intent;
import com.diet.model.SlotBundle;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntentSlotFallbackServiceTest {
    private final IntentSlotFallbackService service = new IntentSlotFallbackService();
    private final Map<String, List<String>> options = Map.of(
            "mealTime", List.of("早餐", "午餐", "晚餐"),
            "healthGoal", List.of("减脂", "高蛋白", "清淡"),
            "taste", List.of("清淡", "辣", "麻辣")
    );

    @Test
    void shouldFillExplicitSignalsWhenModelReturnsEmptySlots() {
        SlotBundle slots = service.mergeExplicitSignals("中午吃什么，想减脂高蛋白而且清淡", SlotBundle.empty(), options);

        assertEquals(List.of("午餐"), slots.mealTime());
        assertTrue(slots.healthGoal().containsAll(List.of("减脂", "高蛋白", "清淡")));
        assertEquals(List.of("清淡"), slots.taste());
        assertEquals(Intent.MEAL_RECOMMENDATION, service.reviseClarifyIntent(Intent.CLARIFY_NEEDED, slots));
    }

    @Test
    void shouldNotOverrideRecognizedModelValues() {
        SlotBundle modelSlots = new SlotBundle(List.of("晚餐"), List.of(), List.of(), List.of("高蛋白"), List.of(), List.of(), List.of());
        SlotBundle slots = service.mergeExplicitSignals("中午想减脂", modelSlots, options);

        assertEquals(List.of("晚餐"), slots.mealTime());
        assertEquals(List.of("高蛋白"), slots.healthGoal());
    }
}
