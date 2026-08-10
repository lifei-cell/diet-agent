package com.diet.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NutritionConstraintsTest {

    @Test
    void shouldKeepExistingAllergenExclusionsAndOverrideSpecifiedNumericConstraint() {
        NutritionConstraints history = new NutritionConstraints(650.0, null, null, null, null, List.of("花生"));
        NutritionConstraints current = new NutritionConstraints(600.0, 30.0, null, null, null, List.of("乳制品", "花生"));

        NutritionConstraints merged = NutritionConstraints.merge(history, current);

        assertEquals(600.0, merged.maxEnergyKcal());
        assertEquals(30.0, merged.minProteinG());
        assertEquals(List.of("花生", "乳制品"), merged.excludedAllergens());
    }
}
