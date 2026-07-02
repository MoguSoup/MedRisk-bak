package com.hbut.medrisk.dto;

public record LlmProfileRequest(
        String displayName,
        String provider,
        String baseUrl,
        String modelName,
        String apiKey,
        Boolean reasoningSupported,
        String reasoningProtocol,
        Boolean enabled,
        Boolean defaultProfile) {
}
