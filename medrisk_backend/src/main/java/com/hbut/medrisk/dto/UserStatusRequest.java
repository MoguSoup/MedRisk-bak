package com.hbut.medrisk.dto;

import jakarta.validation.constraints.NotBlank;

public record UserStatusRequest(@NotBlank String status) {
}
