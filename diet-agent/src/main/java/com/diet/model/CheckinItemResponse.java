package com.diet.model;

public record CheckinItemResponse(
        Long id,
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
