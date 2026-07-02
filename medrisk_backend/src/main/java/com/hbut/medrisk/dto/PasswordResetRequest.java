package com.hbut.medrisk.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
        @Email @NotBlank String email,
        @NotBlank String emailCode,
        @Size(min = 6) String newPassword) {
}
