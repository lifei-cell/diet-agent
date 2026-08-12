package com.diet.service.meal;

import com.diet.enums.SourceMode;
import com.diet.model.MealItem;
import com.diet.model.NutritionConstraints;
import com.diet.model.SlotBundle;

import java.util.List;

/** 混合召回层依赖的主库候选访问契约，便于替换检索后端并隔离单元测试。 */
public interface MealCandidateRepository {
    List<MealItem> searchStructured(SourceMode sourceMode, Long userId, SlotBundle slots, NutritionConstraints nutritionConstraints);

    /**
     * 关键词语料的受限候选集。权限、营养和过敏原均在主库完成硬过滤，排序交由关键词检索器。
     */
    List<MealItem> searchKeywordCorpus(
            SourceMode sourceMode,
            Long userId,
            NutritionConstraints nutritionConstraints,
            int limit
    );

    List<MealItem> findAccessibleByIds(SourceMode sourceMode, Long userId, List<Long> ids);
}
