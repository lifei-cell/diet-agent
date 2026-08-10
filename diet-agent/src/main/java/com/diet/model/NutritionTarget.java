package com.diet.model;

import com.diet.enums.ProfileGoal;

/** Estimated daily nutrition targets calculated from a user's non-clinical profile. */
public record NutritionTarget(
        Double maintenanceEnergyKcal,
        Double dailyEnergyKcal,
        Double dailyProteinG,
        Double dailyFatG,
        Double dailyCarbohydrateG,
        ProfileGoal profileGoal,
        String calculationNote
) {
}
