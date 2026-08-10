package com.diet.model;

public record AuthenticatedUser(Long id, String username, String displayName) {
    public static AuthenticatedUser from(AppUser user) {
        return new AuthenticatedUser(user.getId(), user.getUsername(), user.getDisplayName());
    }
}
