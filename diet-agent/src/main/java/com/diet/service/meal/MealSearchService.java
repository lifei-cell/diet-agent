package com.diet.service.meal;

import com.diet.exception.DietException;
import com.diet.model.MealItem;
import com.diet.model.MealSearchRequest;
import com.diet.enums.SourceMode;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * 餐食检索服务（Orchestrator 推荐流水线第一层）。
 * 使用结构化、关键词、可选向量三路召回；营养、过敏原和数据源权限仍由本服务主库保障。
 */
@Service
public class MealSearchService {

    /** 混合召回服务，负责多路候选生成和 RRF 融合。 */
    private final HybridMealRetrievalService hybridMealRetrievalService;

    /** 构造器注入 MealService。 */
    public MealSearchService(HybridMealRetrievalService hybridMealRetrievalService) {
        this.hybridMealRetrievalService = hybridMealRetrievalService;
    }

    /**
     * 执行数据源隔离检索。
     * 由 Orchestrator#completeRecommendation 调用；excludeMealIds 在 MealRankService 层过滤。
     */
    public List<MealItem> search(MealSearchRequest request) {
        // 请求体或 sourceMode 为空时抛异常
        if (request == null || request.sourceMode() == null) {
            throw new DietException("sourceMode 不能为空");
        }

        // PERSONAL 模式必须提供 userId，否则无法查个人库
        if (request.sourceMode() == SourceMode.PERSONAL && request.userId() == null) {
            throw new DietException("PERSONAL 模式必须提供 userId");
        }

        return hybridMealRetrievalService.retrieve(request);
    }
}
