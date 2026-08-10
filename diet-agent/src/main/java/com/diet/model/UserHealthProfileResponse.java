package com.diet.model;

import com.diet.enums.ActivityLevel;
import com.diet.enums.ProfileGoal;

import java.util.List;

public record UserHealthProfileResponse(
        boolean configured,
        Double heightCm,
        Double weightKg,
        Integer age,
        ActivityLevel activityLevel,
        List<String> diseaseHistory,
        ProfileGoal profileGoal,
        NutritionTarget nutritionTarget,
        String medicalDisclaimer
) {
    public static UserHealthProfileResponse empty() {
        return new UserHealthProfileResponse(false, null, null, null, null, List.of(), null, null,
                "请先完善档案以生成日常营养目标；疾病相关饮食请咨询医生或注册营养师。");
    }
}
