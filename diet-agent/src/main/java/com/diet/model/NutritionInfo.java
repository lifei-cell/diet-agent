package com.diet.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 单份餐食的结构化营养与过敏原数据。
 * 数值允许为空，表示该餐食尚未完成营养标注，不能用于对应的硬约束检索。
 */
@Data
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@AllArgsConstructor
@NoArgsConstructor
public class NutritionInfo {
    private Double energyKcal;
    private Double proteinG;
    private Double fatG;
    private Double carbohydrateG;
    private Double fiberG;
    private Double sodiumMg;
    private List<String> allergens;
    private String nutritionSource;

    public static NutritionInfo empty() {
        return new NutritionInfo(null, null, null, null, null, null, List.of(), null);
    }
}
