package com.diet.model;

import java.util.List;

/** Recognition draft returned after image upload; it is not yet a confirmed dietary record. */
public record ImageRecognitionResponse(
        String recognitionId,
        boolean automated,
        String message,
        List<FoodRecognitionItem> items,
        NutritionTotals totals
) {
}
