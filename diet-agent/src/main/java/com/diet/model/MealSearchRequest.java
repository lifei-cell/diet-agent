package com.diet.model;

import java.util.List;

import com.diet.enums.SourceMode;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 餐食检索请求。
 * 将 sourceMode、userId、slots 和 excludeMealIds 显式打包，避免个人库和公共库混查。
 */
@Data
@Accessors(fluent = true)
public class MealSearchRequest {
    /** 本轮明确选择的数据源模式，不能为空。 */
    private SourceMode sourceMode;
    /** 当前用户 ID，PERSONAL 模式必须使用。 */
    private Long userId;
    /** 用于 MySQL JSON_OVERLAPS 的标准槽位。 */
    private SlotBundle slots;
    /** 必须满足的营养与过敏原限制。 */
    private NutritionConstraints nutritionConstraints;
    /** 需要从结果中排除的上一轮推荐餐食。 */
    private List<Long> excludeMealIds;
    /**
     * 用户本轮原话，仅用于关键词/向量召回扩大候选集；不得用于绕过营养、过敏原和数据权限过滤。
     */
    private String queryText;

    /** 保持既有调用兼容；未传原话时仅使用结构化槽位和槽位标签做召回。 */
    public MealSearchRequest(
            SourceMode sourceMode,
            Long userId,
            SlotBundle slots,
            NutritionConstraints nutritionConstraints,
            List<Long> excludeMealIds
    ) {
        this(sourceMode, userId, slots, nutritionConstraints, excludeMealIds, null);
    }

    public MealSearchRequest(
            SourceMode sourceMode,
            Long userId,
            SlotBundle slots,
            NutritionConstraints nutritionConstraints,
            List<Long> excludeMealIds,
            String queryText
    ) {
        this.sourceMode = sourceMode;
        this.userId = userId;
        this.slots = slots;
        this.nutritionConstraints = nutritionConstraints;
        this.excludeMealIds = excludeMealIds;
        this.queryText = queryText == null ? null : queryText.trim();
    }
}
