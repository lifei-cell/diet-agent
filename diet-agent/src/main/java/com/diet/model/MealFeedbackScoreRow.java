package com.diet.model;

import lombok.Data;

/** 用户对单个餐食的聚合反馈分。 */
@Data
public class MealFeedbackScoreRow {
    private Long itemId;
    private Double feedbackScore;
}
