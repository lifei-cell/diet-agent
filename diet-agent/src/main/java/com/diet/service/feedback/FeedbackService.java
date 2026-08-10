package com.diet.service.feedback;

import com.diet.exception.DietException;
import com.diet.enums.FeedbackAction;
import com.diet.mapper.FeedbackMapper;
import com.diet.mapper.SessionMapper;
import com.diet.model.FeedbackRequest;
import com.diet.model.MealItem;
import com.diet.service.meal.MealService;
import com.diet.service.preference.UserPreferenceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedbackService {
    private final FeedbackMapper feedbackMapper;
    private final SessionMapper sessionMapper;
    private final MealService mealService;
    private final UserPreferenceService userPreferenceService;

    public FeedbackService(
            FeedbackMapper feedbackMapper,
            SessionMapper sessionMapper,
            MealService mealService,
            UserPreferenceService userPreferenceService
    ) {
        this.feedbackMapper = feedbackMapper;
        this.sessionMapper = sessionMapper;
        this.mealService = mealService;
        this.userPreferenceService = userPreferenceService;
    }

    @Transactional
    public void save(Long userId, FeedbackRequest request) {
        if (request == null || request.sessionId() == null || request.sessionId().isBlank()) {
            throw new DietException("反馈 sessionId 不能为空");
        }
        if (sessionMapper.findById(request.sessionId(), userId) == null) {
            throw new DietException("会话不存在或无权限反馈");
        }
        FeedbackAction action = FeedbackAction.from(request.action());
        if (action == FeedbackAction.RATING && request.rating() == null) {
            throw new DietException("RATING 反馈必须提供 1 到 5 的 rating");
        }
        if (request.rating() != null && (request.rating() < 1 || request.rating() > 5)) {
            throw new DietException("rating 必须在 1 到 5 之间");
        }

        MealItem meal = null;
        if (request.itemId() != null) {
            meal = mealService.findAccessibleMealById(userId, request.itemId());
            if (meal == null) {
                throw new DietException("餐食不存在或无权限反馈");
            }
        }
        feedbackMapper.insert(
                userId,
                request.sessionId(),
                request.itemId(),
                action.name(),
                request.rating(),
                request.reason()
        );
        if (meal != null) {
            userPreferenceService.applyFeedback(userId, meal, action, request.rating());
        }
    }
}
