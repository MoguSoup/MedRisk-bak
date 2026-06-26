package com.hbut.medrisk.dto;

import jakarta.validation.constraints.Size;

public record AdminPasswordResetRequest(@Size(min = 6) String password) {
}
