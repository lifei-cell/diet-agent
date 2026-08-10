package com.diet.model;

/** 单个候选餐食的可解释重排分项。 */
public record MealRankScore(
        Long itemId,
        String name,
        double contextScore,
        double preferenceScore,
        double feedbackScore,
        double finalScore
) {
}
