package com.diet.model;

import java.time.LocalDate;
import java.util.List;

public record DietCheckinResponse(
        Long id,
        LocalDate checkinDate,
        String mealTime,
        NutritionTotals totals,
        List<CheckinItemResponse> items,
        String createdAt
) {
}
