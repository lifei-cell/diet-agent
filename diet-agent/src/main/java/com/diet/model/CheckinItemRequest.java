package com.diet.model;

/** User-confirmed or user-corrected food entry submitted for a check-in. */
public record CheckinItemRequest(
        String name,
        Double estimatedWeightG,
        Double energyKcal,
        Double proteinG,
        Double fatG,
        Double carbohydrateG,
        Double confidence,
        String nutritionSource
) {
}
