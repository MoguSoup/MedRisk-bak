package com.hbut.medrisk.controller;

import com.hbut.medrisk.dto.ApiResponse;
import com.hbut.medrisk.dto.PredictionResponse;
import com.hbut.medrisk.entity.UserEntity;
import com.hbut.medrisk.service.AuthService;
import com.hbut.medrisk.service.PredictionService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PredictionController {
    private final AuthService authService;
    private final PredictionService predictionService;

    public PredictionController(AuthService authService, PredictionService predictionService) {
        this.authService = authService;
        this.predictionService = predictionService;
    }

    @PostMapping("/predict/{diseaseType}")
    ApiResponse<PredictionResponse> predict(
            @PathVariable String diseaseType,
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        UserEntity user = authService.requireUser(authorization);
        authService.requireAnyRole(user, "PATIENT", "DOCTOR", "ADMIN");
        return ApiResponse.ok(predictionService.predict(diseaseType, payload, user));
    }

    @GetMapping("/predict/explain/{recordId}")
    ApiResponse<PredictionResponse> explain(
            @PathVariable Long recordId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        UserEntity user = authService.requireUser(authorization);
        return ApiResponse.ok(predictionService.explain(recordId, user));
    }

    @GetMapping("/history/predictions")
    ApiResponse<List<PredictionResponse>> history(@RequestHeader(value = "Authorization", required = false) String authorization) {
        UserEntity user = authService.requireUser(authorization);
        return ApiResponse.ok(predictionService.history(user));
    }
}
