package com.diet.service.profile;

import com.diet.enums.ActivityLevel;
import com.diet.enums.ProfileGoal;
import com.diet.model.NutritionTarget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NutritionTargetCalculatorTest {

    private final NutritionTargetCalculator calculator = new NutritionTargetCalculator();

    @Test
    void shouldDeriveGoalSpecificEnergyAndMacroTargets() {
        NutritionTarget maintain = calculator.calculate(
                175, 70, 28, ActivityLevel.MODERATE, ProfileGoal.MAINTAIN);
        NutritionTarget fatLoss = calculator.calculate(
                175, 70, 28, ActivityLevel.MODERATE, ProfileGoal.FAT_LOSS);
        NutritionTarget muscleGain = calculator.calculate(
                175, 70, 28, ActivityLevel.MODERATE, ProfileGoal.MUSCLE_GAIN);

        assertTrue(fatLoss.dailyEnergyKcal() < maintain.dailyEnergyKcal());
        assertTrue(muscleGain.dailyEnergyKcal() > maintain.dailyEnergyKcal());
        assertEquals(112.0, fatLoss.dailyProteinG());
        assertEquals(126.0, muscleGain.dailyProteinG());
        assertTrue(fatLoss.dailyFatG() >= 40.0);
        assertTrue(fatLoss.dailyCarbohydrateG() >= 0.0);
    }
}
