package com.diet.model;

/** A set of nutrition values aggregated for one check-in or one day. */
public record NutritionTotals(
        Double energyKcal,
        Double proteinG,
        Double fatG,
        Double carbohydrateG
) {
    public static NutritionTotals empty() {
        return new NutritionTotals(0.0, 0.0, 0.0, 0.0);
    }
}
