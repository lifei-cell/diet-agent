package com.diet.model;

/** meal_item 的标准化倒排标签行，用于索引化结构化召回。 */
public record MealSlotTag(Long mealId, String slotName, String tagValue) {
}
