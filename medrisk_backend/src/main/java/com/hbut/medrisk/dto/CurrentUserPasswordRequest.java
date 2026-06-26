package com.hbut.medrisk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CurrentUserPasswordRequest(
        @NotBlank String oldPassword,
        @NotBlank @Size(min = 6) String newPassword) {
}
