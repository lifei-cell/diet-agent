package com.diet.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AppUser {
    private Long id;
    private String username;
    private String passwordHash;
    private String displayName;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
