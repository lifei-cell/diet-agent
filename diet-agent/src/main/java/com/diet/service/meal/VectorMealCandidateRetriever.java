package com.diet.service.meal;

import com.diet.model.MealSearchRequest;

import java.util.List;

/** 可替换的向量候选 ID 召回契约。 */
public interface VectorMealCandidateRetriever {
    List<Long> recall(MealSearchRequest request);
}
