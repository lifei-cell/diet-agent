package com.diet.service.meal;

import com.diet.enums.SourceMode;
import com.diet.model.MealItem;
import com.diet.model.NutritionConstraints;
import com.diet.model.NutritionInfo;
import com.diet.model.SlotBundle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MealConstraintMatcherTest {
    private final MealConstraintMatcher matcher = new MealConstraintMatcher();

    @Test
    void shouldRejectVectorCandidateThatViolatesAllergenOrNutritionConstraint() {
        MealItem meal = new MealItem(1L, SourceMode.PUBLIC, null, "花生鸡肉饭", SlotBundle.empty(),
                new NutritionInfo(650.0, 35.0, 20.0, 60.0, 5.0, 600.0, List.of("花生"), "TEST"), 0);

        assertFalse(matcher.matches(meal, new NutritionConstraints(600.0, 30.0, null, null, null, List.of())));
        assertFalse(matcher.matches(meal, new NutritionConstraints(null, null, null, null, null, List.of("花生"))));
        assertTrue(matcher.matches(meal, new NutritionConstraints(700.0, 30.0, null, null, null, List.of())));
    }
}
