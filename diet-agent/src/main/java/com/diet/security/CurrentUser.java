package com.diet.security;

import com.diet.model.AuthenticatedUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

public final class CurrentUser {
    private CurrentUser() {
    }

    public static AuthenticatedUser require(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        throw new AccessDeniedException("未登录或登录状态已失效");
    }
}
