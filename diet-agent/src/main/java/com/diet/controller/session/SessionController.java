package com.diet.controller.session;

import com.diet.model.CreateSessionResponse;
import com.diet.security.CurrentUser;
import com.diet.service.session.SessionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/diet/sessions")
public class SessionController {
    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    public CreateSessionResponse create(Authentication authentication) {
        return new CreateSessionResponse(sessionService.createSession(CurrentUser.require(authentication).id()));
    }
}
