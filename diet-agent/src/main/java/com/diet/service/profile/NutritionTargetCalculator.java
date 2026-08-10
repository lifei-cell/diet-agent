package com.diet.service.profile;

import com.diet.enums.ActivityLevel;
import com.diet.enums.ProfileGoal;
import com.diet.model.NutritionTarget;
import org.springframework.stereotype.Component;

/**
 * Produces non-clinical daily nutrition targets from basic profile data.
 * The result is a starting point for meal planning and must not replace medical advice.
 */
@Component
public class NutritionTargetCalculator {

    public NutritionTarget calculate(
            double heightCm,
            double weightKg,
            int age,
            ActivityLevel activityLevel,
            ProfileGoal profileGoal
    ) {
        // A gender-neutral midpoint of the Mifflin-St Jeor constants; it is deliberately labelled as an estimate.
        double basalMetabolicRate = Math.max(1100, 10 * weightKg + 6.25 * heightCm - 5 * age - 78);
        double maintenanceEnergy = round(basalMetabolicRate * activityLevel.multiplier());
        double dailyEnergy = round(Math.max(1200, maintenanceEnergy + profileGoal.dailyEnergyAdjustment()));
        double protein = round(weightKg * profileGoal.proteinPerKg());
        double fat = round(Math.max(40, dailyEnergy * 0.25 / 9));
        double carbohydrate = round(Math.max(0, (dailyEnergy - protein * 4 - fat * 9) / 4));

        return new NutritionTarget(
                maintenanceEnergy,
                dailyEnergy,
                protein,
                fat,
                carbohydrate,
                profileGoal,
                "按身高、体重、年龄、活动水平和目标估算的每日起始目标；未采集体脂率等信息，建议结合身体反馈调整。"
        );
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
