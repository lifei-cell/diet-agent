package com.diet.model;

import java.time.LocalDate;
import java.util.List;

public record CheckinConfirmRequest(
        String recognitionId,
        LocalDate checkinDate,
        String mealTime,
        List<CheckinItemRequest> items
) {
}
