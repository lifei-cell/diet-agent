package com.diet.controller.feedback;

import com.diet.model.FeedbackRequest;
import com.diet.security.CurrentUser;
import com.diet.service.feedback.FeedbackService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/diet/feedback")
public class FeedbackController {
    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    public void save(
            Authentication authentication,
            @RequestBody FeedbackRequest request
    ) {
        feedbackService.save(CurrentUser.require(authentication).id(), request);
    }
}




