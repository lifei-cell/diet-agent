package com.diet.service.profile;

import com.diet.enums.ActivityLevel;
import com.diet.enums.ProfileGoal;
import com.diet.exception.DietException;
import com.diet.mapper.UserHealthProfileMapper;
import com.diet.model.NutritionTarget;
import com.diet.model.UserHealthProfileRequest;
import com.diet.model.UserHealthProfileResponse;
import com.diet.model.UserHealthProfileRow;
import com.diet.util.JsonService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserHealthProfileService {

    private final UserHealthProfileMapper profileMapper;
    private final NutritionTargetCalculator nutritionTargetCalculator;
    private final JsonService jsonService;

    public UserHealthProfileService(
            UserHealthProfileMapper profileMapper,
            NutritionTargetCalculator nutritionTargetCalculator,
            JsonService jsonService
    ) {
        this.profileMapper = profileMapper;
        this.nutritionTargetCalculator = nutritionTargetCalculator;
        this.jsonService = jsonService;
    }

    public UserHealthProfileResponse findByUserId(Long userId) {
        UserHealthProfileRow row = profileMapper.findByUserId(userId);
        return row == null ? UserHealthProfileResponse.empty() : toResponse(row);
    }

    public NutritionTarget findNutritionTarget(Long userId) {
        UserHealthProfileRow row = profileMapper.findByUserId(userId);
        return row == null ? null : calculate(row);
    }

    @Transactional
    public UserHealthProfileResponse save(Long userId, UserHealthProfileRequest request) {
        validate(request);
        UserHealthProfileRow row = new UserHealthProfileRow();
        row.setUserId(userId);
        row.setHeightCm(request.heightCm());
        row.setWeightKg(request.weightKg());
        row.setAge(request.age());
        row.setActivityLevel(request.activityLevel().name());
        row.setDiseaseHistory(jsonService.toJsonArray(normalizeDiseaseHistory(request.diseaseHistory())));
        row.setProfileGoal(request.profileGoal().name());
        profileMapper.upsert(row);
        return findByUserId(userId);
    }

    private UserHealthProfileResponse toResponse(UserHealthProfileRow row) {
        ActivityLevel activityLevel = parseActivityLevel(row.getActivityLevel());
        ProfileGoal profileGoal = parseProfileGoal(row.getProfileGoal());
        List<String> diseaseHistory = normalizeDiseaseHistory(jsonService.fromJsonArray(row.getDiseaseHistory()));
        NutritionTarget target = nutritionTargetCalculator.calculate(
                row.getHeightCm(), row.getWeightKg(), row.getAge(), activityLevel, profileGoal);
        String disclaimer = diseaseHistory.isEmpty()
                ? "该目标用于日常饮食参考，不替代医生或注册营养师的建议。"
                : "已记录疾病史。该目标不用于诊断、治疗或替代医嘱；涉及疾病、用药或特殊人群时请咨询医生或注册营养师。";
        return new UserHealthProfileResponse(
                true,
                row.getHeightCm(),
                row.getWeightKg(),
                row.getAge(),
                activityLevel,
                diseaseHistory,
                profileGoal,
                target,
                disclaimer
        );
    }

    private NutritionTarget calculate(UserHealthProfileRow row) {
        return nutritionTargetCalculator.calculate(
                row.getHeightCm(), row.getWeightKg(), row.getAge(),
                parseActivityLevel(row.getActivityLevel()), parseProfileGoal(row.getProfileGoal()));
    }

    private void validate(UserHealthProfileRequest request) {
        if (request == null) {
            throw new DietException("用户档案不能为空");
        }
        validateRange(request.heightCm(), 80, 250, "身高");
        validateRange(request.weightKg(), 25, 500, "体重");
        if (request.age() == null || request.age() < 14 || request.age() > 120) {
            throw new DietException("年龄须在 14 到 120 岁之间");
        }
        if (request.activityLevel() == null) {
            throw new DietException("请选择运动频率");
        }
        if (request.profileGoal() == null) {
            throw new DietException("请选择健康目标");
        }
        normalizeDiseaseHistory(request.diseaseHistory());
    }

    private void validateRange(Double value, double min, double max, String label) {
        if (value == null || !Double.isFinite(value) || value < min || value > max) {
            throw new DietException(label + "数值不在合理范围内");
        }
    }

    private List<String> normalizeDiseaseHistory(List<String> diseaseHistory) {
        List<String> normalized = diseaseHistory == null ? List.of() : diseaseHistory.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (normalized.size() > 20 || normalized.stream().anyMatch(item -> item.length() > 64)) {
            throw new DietException("疾病史最多 20 项，单项不能超过 64 个字符");
        }
        return normalized;
    }

    private ActivityLevel parseActivityLevel(String value) {
        try {
            return ActivityLevel.valueOf(value);
        } catch (Exception exception) {
            throw new DietException("用户档案中的运动频率无效");
        }
    }

    private ProfileGoal parseProfileGoal(String value) {
        try {
            return ProfileGoal.valueOf(value);
        } catch (Exception exception) {
            throw new DietException("用户档案中的健康目标无效");
        }
    }
}
