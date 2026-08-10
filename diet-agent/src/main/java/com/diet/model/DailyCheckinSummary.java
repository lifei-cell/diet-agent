package com.diet.model;

import java.time.LocalDate;
import java.util.List;

/** Daily food log and its comparison to the current user's daily nutrition target. */
public record DailyCheckinSummary(
        LocalDate date,
        List<DietCheckinResponse> checkins,
        NutritionTotals consumed,
        NutritionTarget nutritionTarget,
        NutritionTotals remaining,
        String message
) {
}
