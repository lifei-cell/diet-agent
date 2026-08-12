package com.diet.service.meal;

import com.diet.model.SlotBundle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotQueryExpansionServiceTest {
    private final SlotQueryExpansionService service = new SlotQueryExpansionService();

    @Test
    void shouldExpandControlledHealthGoalSynonymsWithoutChangingOtherSlots() {
        SlotBundle expanded = service.expand(new SlotBundle(
                List.of("午餐"), List.of(), List.of(), List.of("低脂"), List.of(), List.of(), List.of()));

        assertTrue(expanded.healthGoal().containsAll(List.of("低脂", "减脂", "低油", "清淡")));
        assertTrue(expanded.mealTime().equals(List.of("午餐")));
    }
}
