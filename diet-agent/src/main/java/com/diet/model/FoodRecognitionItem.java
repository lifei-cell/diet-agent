package com.diet.model;

/** One food item estimated from an uploaded meal image before the user confirms it. */
public record FoodRecognitionItem(
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
