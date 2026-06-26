package com.hbut.medrisk.dto;

import java.util.List;
import java.util.Map;

public record TrainingHistoryResponse(Long jobId, Map<String, List<Double>> history) {
}
