package com.diet.model;

import com.diet.enums.ActivityLevel;
import com.diet.enums.ProfileGoal;

import java.util.List;

public record UserHealthProfileRequest(
        Double heightCm,
        Double weightKg,
        Integer age,
        ActivityLevel activityLevel,
        List<String> diseaseHistory,
        ProfileGoal profileGoal
) {
}
