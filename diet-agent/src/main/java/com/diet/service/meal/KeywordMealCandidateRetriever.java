package com.diet.service.meal;

import com.diet.model.MealItem;
import com.diet.model.MealSearchRequest;

import java.util.List;

/**
 * 关键词候选召回的扩展点。当前实现为应用侧 BM25，菜品库扩容后可替换为搜索引擎实现，
 * 而不影响 HybridMealRetrievalService 的 RRF 融合逻辑。
 */
public interface KeywordMealCandidateRetriever {
    List<MealItem> recall(MealSearchRequest request, List<String> queryTerms);
}
