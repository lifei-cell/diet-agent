package com.diet.model;

public record AuthResponse(String accessToken, String tokenType, long expiresIn, AuthenticatedUser user) {
}
