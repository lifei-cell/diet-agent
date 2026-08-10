package com.diet.model;

import java.util.List;

/** 重排后的餐食列表及其分项得分，供编排层展示和 Trace 记录。 */
public record MealRankResult(List<MealItem> meals, List<MealRankScore> scores) {
}
