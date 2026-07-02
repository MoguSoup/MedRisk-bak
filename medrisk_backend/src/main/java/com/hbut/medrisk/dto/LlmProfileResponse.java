package com.hbut.medrisk.dto;

import java.time.LocalDateTime;

public record LlmProfileResponse(
        Long id,
        String displayName,
        String provider,
        String baseUrl,
        String modelName,
        String maskedApiKey,
        Boolean hasApiKey,
        Boolean reasoningSupported,
        String reasoningProtocol,
        Boolean enabled,
        Boolean defaultProfile,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
