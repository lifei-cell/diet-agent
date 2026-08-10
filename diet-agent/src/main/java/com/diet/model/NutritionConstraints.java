package com.diet.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * 用户明确提出的营养硬约束。
 * 数值字段为上限或下限；excludedAllergens 中任一项命中都会在检索阶段剔除餐食。
 */
@Data
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@AllArgsConstructor
@NoArgsConstructor
public class NutritionConstraints {
    private Double maxEnergyKcal;
    private Double minProteinG;
    private Double maxFatG;
    private Double maxCarbohydrateG;
    private Double maxSodiumMg;
    private List<String> excludedAllergens;

    public static NutritionConstraints empty() {
        return new NutritionConstraints(null, null, null, null, null, List.of());
    }

    /**
     * 数值约束以本轮非空值覆盖历史；过敏原限制累计合并，避免多轮对话中遗失安全限制。
     */
    public static NutritionConstraints merge(NutritionConstraints history, NutritionConstraints current) {
        NutritionConstraints base = sanitize(history);
        NutritionConstraints update = sanitize(current);
        LinkedHashSet<String> allergens = new LinkedHashSet<>(base.excludedAllergens());
        allergens.addAll(update.excludedAllergens());
        return new NutritionConstraints(
                update.maxEnergyKcal() == null ? base.maxEnergyKcal() : update.maxEnergyKcal(),
                update.minProteinG() == null ? base.minProteinG() : update.minProteinG(),
                update.maxFatG() == null ? base.maxFatG() : update.maxFatG(),
                update.maxCarbohydrateG() == null ? base.maxCarbohydrateG() : update.maxCarbohydrateG(),
                update.maxSodiumMg() == null ? base.maxSodiumMg() : update.maxSodiumMg(),
                List.copyOf(allergens)
        );
    }

    public static NutritionConstraints sanitize(NutritionConstraints value) {
        if (value == null) {
            return empty();
        }
        return new NutritionConstraints(
                nonNegative(value.maxEnergyKcal()),
                nonNegative(value.minProteinG()),
                nonNegative(value.maxFatG()),
                nonNegative(value.maxCarbohydrateG()),
                nonNegative(value.maxSodiumMg()),
                normalizeStrings(value.excludedAllergens())
        );
    }

    public boolean isEmpty() {
        return maxEnergyKcal == null
                && minProteinG == null
                && maxFatG == null
                && maxCarbohydrateG == null
                && maxSodiumMg == null
                && (excludedAllergens == null || excludedAllergens.isEmpty());
    }

    private static Double nonNegative(Double value) {
        return value == null || !Double.isFinite(value) || value < 0 ? null : value;
    }

    private static List<String> normalizeStrings(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }
}
