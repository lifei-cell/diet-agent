package com.diet.controller.evaluation;

import com.diet.model.EvaluationReport;
import com.diet.model.EvaluationRequest;
import com.diet.security.CurrentUser;
import com.diet.service.evaluation.EvaluationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/diet/evaluations")
public class EvaluationController {
    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @PostMapping
    public EvaluationReport evaluate(
            Authentication authentication,
            @RequestBody EvaluationRequest request
    ) {
        return evaluationService.evaluate(CurrentUser.require(authentication).id(), request);
    }
}




