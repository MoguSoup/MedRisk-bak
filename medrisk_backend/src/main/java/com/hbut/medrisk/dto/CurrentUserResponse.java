package com.hbut.medrisk.dto;

import java.time.LocalDateTime;

public record CurrentUserResponse(
        Long id,
        String username,
        String email,
        String name,
        String phone,
        String avatarUrl,
        String role,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime lastLoginAt) {
}
