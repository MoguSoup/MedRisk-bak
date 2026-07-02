package com.hbut.medrisk.dto;

import java.util.List;

public record ReportGenerateRequest(
        Long conversationId,
        List<Long> qaMessageIds,
        Boolean includeReasoning) {
}
