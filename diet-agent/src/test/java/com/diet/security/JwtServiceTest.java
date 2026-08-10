package com.diet.security;

import com.diet.model.AppUser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JwtServiceTest {

    @Test
    void shouldIssueAndVerifyTokenForUser() {
        JwtService jwtService = new JwtService(
                "test-jwt-secret-must-be-longer-than-thirty-two-bytes-2026",
                3600
        );
        AppUser user = new AppUser();
        user.setId(42L);
        user.setUsername("diet_user");

        JwtService.JwtSubject subject = jwtService.parse(jwtService.createToken(user));

        assertEquals(42L, subject.userId());
        assertEquals("diet_user", subject.username());
    }
}
