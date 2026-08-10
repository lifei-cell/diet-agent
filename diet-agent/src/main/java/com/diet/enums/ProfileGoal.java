package com.diet.enums;

public enum ProfileGoal {
    FAT_LOSS(-450, 1.6, "减脂"),
    MAINTAIN(0, 1.2, "维持"),
    MUSCLE_GAIN(300, 1.8, "增肌");

    private final int dailyEnergyAdjustment;
    private final double proteinPerKg;
    private final String label;

    ProfileGoal(int dailyEnergyAdjustment, double proteinPerKg, String label) {
        this.dailyEnergyAdjustment = dailyEnergyAdjustment;
        this.proteinPerKg = proteinPerKg;
        this.label = label;
    }

    public int dailyEnergyAdjustment() {
        return dailyEnergyAdjustment;
    }

    public double proteinPerKg() {
        return proteinPerKg;
    }

    public String label() {
        return label;
    }
}
