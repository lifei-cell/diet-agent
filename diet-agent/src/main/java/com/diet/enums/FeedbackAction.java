package com.diet.enums;

import com.diet.exception.DietException;

import java.util.Locale;

/**
 * 用户对推荐餐食的反馈行为。
 * 权重同时用于更新长期槽位偏好，RATING 的中性分为 3 分。
 */
public enum FeedbackAction {
    SELECT,
    LIKE,
    DISLIKE,
    SKIP,
    RATING;

    /**
     * 将接口入参规范化为枚举；ADOPT 是早期前端使用的兼容别名。
     */
    public static FeedbackAction from(String value) {
        if (value == null || value.isBlank()) {
            throw new DietException("反馈 action 不能为空");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("ADOPT".equals(normalized)) {
            return SELECT;
        }
        try {
            return FeedbackAction.valueOf(normalized);
        } catch (IllegalArgumentException error) {
            throw new DietException("不支持的反馈 action，仅支持 SELECT、LIKE、DISLIKE、SKIP、RATING");
        }
    }

    /**
     * 返回写入长期偏好画像的增量权重。
     */
    public double preferenceWeight(Integer rating) {
        return switch (this) {
            case SELECT -> 3.0;
            case LIKE -> 2.0;
            case DISLIKE -> -3.0;
            case SKIP -> -1.0;
            case RATING -> rating == null ? 0.0 : rating - 3.0;
        };
    }
}
