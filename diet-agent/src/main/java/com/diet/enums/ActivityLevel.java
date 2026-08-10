package com.diet.enums;

public enum ActivityLevel {
    SEDENTARY(1.20, "久坐，很少运动"),
    LIGHT(1.375, "轻度活动，每周 1-2 次运动"),
    MODERATE(1.55, "中等活动，每周 3-5 次运动"),
    HIGH(1.725, "高活动量，每周 6-7 次运动"),
    ATHLETE(1.90, "高强度训练或体力劳动");

    private final double multiplier;
    private final String description;

    ActivityLevel(double multiplier, String description) {
        this.multiplier = multiplier;
        this.description = description;
    }

    public double multiplier() {
        return multiplier;
    }

    public String description() {
        return description;
    }
}
