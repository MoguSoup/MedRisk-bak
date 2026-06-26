package com.hbut.medrisk.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminUserRequest(
        @NotBlank String username,
        @Email @NotBlank String email,
        @NotBlank String name,
        String phone,
        @NotBlank String role,
        String status,
        @Size(min = 6) String password) {
}
