package com.hbut.medrisk.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CurrentUserUpdateRequest(
        @Email @NotBlank String email,
        @NotBlank String name,
        String phone) {
}
