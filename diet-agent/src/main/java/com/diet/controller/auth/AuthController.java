package com.diet.controller.auth;

import com.diet.model.AuthResponse;
import com.diet.model.AuthenticatedUser;
import com.diet.model.LoginRequest;
import com.diet.model.RegisterRequest;
import com.diet.security.CurrentUser;
import com.diet.service.auth.AuthService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public AuthResponse register(@RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public AuthenticatedUser me(Authentication authentication) {
        return authService.currentUser(CurrentUser.require(authentication).id());
    }
}
