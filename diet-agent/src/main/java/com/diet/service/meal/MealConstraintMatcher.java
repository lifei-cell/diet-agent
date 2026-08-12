package com.diet.service.meal;

import com.diet.model.MealItem;
import com.diet.model.NutritionConstraints;
import com.diet.model.NutritionInfo;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * 向量通道返回的 ID 必须回到本服务做二次硬约束校验，不能信任外部召回服务。
 */
@Component
public class MealConstraintMatcher {

    public boolean matches(MealItem meal, NutritionConstraints constraints) {
        if (meal == null) {
            return false;
        }
        NutritionConstraints safe = NutritionConstraints.sanitize(constraints);
        NutritionInfo nutrition = meal.nutrition() == null ? NutritionInfo.empty() : meal.nutrition();
        return maxMatches(nutrition.energyKcal(), safe.maxEnergyKcal())
                && minMatches(nutrition.proteinG(), safe.minProteinG())
                && maxMatches(nutrition.fatG(), safe.maxFatG())
                && maxMatches(nutrition.carbohydrateG(), safe.maxCarbohydrateG())
                && maxMatches(nutrition.sodiumMg(), safe.maxSodiumMg())
                && allergensMatch(nutrition.allergens(), safe.excludedAllergens());
    }

    private boolean maxMatches(Double value, Double max) {
        return max == null || (value != null && value <= max);
    }

    private boolean minMatches(Double value, Double min) {
        return min == null || (value != null && value >= min);
    }

    private boolean allergensMatch(java.util.List<String> mealAllergens, java.util.List<String> excluded) {
        if (excluded == null || excluded.isEmpty()) {
            return true;
        }
        Set<String> values = new HashSet<>(mealAllergens == null ? java.util.List.of() : mealAllergens);
        return excluded.stream().noneMatch(values::contains);
    }
}
