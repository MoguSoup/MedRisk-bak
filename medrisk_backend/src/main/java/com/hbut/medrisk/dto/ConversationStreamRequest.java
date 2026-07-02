package com.hbut.medrisk.dto;

public record ConversationStreamRequest(
        String question,
        Long modelProfileId,
        Boolean reasoningEnabled,
        String chatMode,
        Boolean outputImageRequested,
        String imageBase64,
        String imageContentType) {
}
