package com.hbut.medrisk.controller;

import com.hbut.medrisk.dto.ApiResponse;
import com.hbut.medrisk.dto.ModelEvaluationRequest;
import com.hbut.medrisk.dto.ModelEvaluationResponse;
import com.hbut.medrisk.dto.ModelFeedbackRequest;
import com.hbut.medrisk.dto.ModelFeedbackResponse;
import com.hbut.medrisk.dto.ModelVersionResponse;
import com.hbut.medrisk.dto.TrainingDatasetResponse;
import com.hbut.medrisk.dto.TrainingHistoryResponse;
import com.hbut.medrisk.dto.TrainingJobRequest;
import com.hbut.medrisk.dto.TrainingJobResponse;
import com.hbut.medrisk.entity.UserEntity;
import com.hbut.medrisk.service.AdminTrainingService;
import com.hbut.medrisk.service.AuthService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin")
public class AdminTrainingController {
    private final AuthService authService;
    private final AdminTrainingService trainingService;

    public AdminTrainingController(AuthService authService, AdminTrainingService trainingService) {
        this.authService = authService;
        this.trainingService = trainingService;
    }

    @GetMapping("/datasets")
    ApiResponse<List<TrainingDatasetResponse>> datasets(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAdmin(authorization);
        return ApiResponse.ok(trainingService.listDatasets());
    }

    @PostMapping(value = "/datasets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<TrainingDatasetResponse> uploadDataset(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam String name,
            @RequestParam String diseaseType,
            @RequestParam(required = false) String description,
            @RequestParam MultipartFile file) throws IOException {
        UserEntity user = requireAdmin(authorization);
        return ApiResponse.ok(trainingService.uploadDataset(name, diseaseType, description, file, user));
    }

    @PostMapping("/datasets/import-public")
    ApiResponse<List<TrainingDatasetResponse>> importPublicDatasets(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        UserEntity user = requireAdmin(authorization);
        return ApiResponse.ok(trainingService.importPublicDatasets(user));
    }

    @PutMapping("/datasets/{id}")
    ApiResponse<TrainingDatasetResponse> updateDataset(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload) {
        requireAdmin(authorization);
        return ApiResponse.ok(trainingService.updateDataset(id, payload));
    }

    @DeleteMapping("/datasets/{id}")
    ApiResponse<Map<String, Boolean>> deleteDataset(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id) {
        requireAdmin(authorization);
        trainingService.deleteDataset(id);
        return ApiResponse.ok(Map.of("deleted", true));
    }

    @PostMapping("/datasets/{id}/validate")
    ApiResponse<TrainingDatasetResponse> validateDataset(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id) {
        UserEntity user = requireAdmin(authorization);
        return ApiResponse.ok(trainingService.validateDataset(id, user));
    }

    @GetMapping("/training-jobs")
    ApiResponse<List<TrainingJobResponse>> trainingJobs(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAdmin(authorization);
        return ApiResponse.ok(trainingService.listJobs());
    }

    @GetMapping("/model-capabilities")
    ApiResponse<List<Map<String, Object>>> modelCapabilities(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAdmin(authorization);
        return ApiResponse.ok(trainingService.modelCapabilities());
    }

    @PostMapping("/training-jobs")
    ApiResponse<TrainingJobResponse> createTrainingJob(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody TrainingJobRequest request) {
        UserEntity user = requireAdmin(authorization);
        return ApiResponse.ok(trainingService.createJob(request, user));
    }

    @DeleteMapping("/training-jobs/{id}")
    ApiResponse<Map<String, Boolean>> deleteTrainingJob(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id) {
        requireAdmin(authorization);
        trainingService.deleteJob(id);
        return ApiResponse.ok(Map.of("deleted", true));
    }

    @PostMapping("/training-jobs/{id}/stop")
    ApiResponse<TrainingJobResponse> stopTrainingJob(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id) {
        UserEntity user = requireAdmin(authorization);
        return ApiResponse.ok(trainingService.stopJob(id, user));
    }

    @GetMapping("/training-jobs/{id}/status")
    ApiResponse<TrainingJobResponse> trainingJobStatus(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id) {
        requireAdmin(authorization);
        return ApiResponse.ok(trainingService.getJobStatus(id));
    }

    @GetMapping("/training-jobs/{id}/history")
    ApiResponse<TrainingHistoryResponse> trainingJobHistory(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id) {
        requireAdmin(authorization);
        return ApiResponse.ok(trainingService.getJobHistory(id));
    }

    @PostMapping("/models/{modelVersionId}/activate")
    ApiResponse<ModelVersionResponse> activateModel(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long modelVersionId) {
        UserEntity user = requireAdmin(authorization);
        return ApiResponse.ok(trainingService.activateModel(modelVersionId, user));
    }

    @GetMapping("/model-evaluations")
    ApiResponse<List<ModelEvaluationResponse>> modelEvaluations(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAdmin(authorization);
        return ApiResponse.ok(trainingService.listEvaluations());
    }

    @PostMapping("/model-evaluations")
    ApiResponse<ModelEvaluationResponse> createEvaluation(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody ModelEvaluationRequest request) {
        UserEntity user = requireAdmin(authorization);
        return ApiResponse.ok(trainingService.createEvaluation(request, user));
    }

    @GetMapping("/model-feedback")
    ApiResponse<List<ModelFeedbackResponse>> modelFeedback(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAdmin(authorization);
        return ApiResponse.ok(trainingService.listFeedback());
    }

    @PostMapping("/model-feedback")
    ApiResponse<ModelFeedbackResponse> createFeedback(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody ModelFeedbackRequest request) {
        UserEntity user = requireAdmin(authorization);
        return ApiResponse.ok(trainingService.createFeedback(request, user));
    }

    @PutMapping("/model-feedback/{id}")
    ApiResponse<ModelFeedbackResponse> updateFeedback(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id,
            @Valid @RequestBody ModelFeedbackRequest request) {
        requireAdmin(authorization);
        return ApiResponse.ok(trainingService.updateFeedback(id, request));
    }

    @DeleteMapping("/model-feedback/{id}")
    ApiResponse<Map<String, Boolean>> deleteFeedback(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id) {
        requireAdmin(authorization);
        trainingService.deleteFeedback(id);
        return ApiResponse.ok(Map.of("deleted", true));
    }

    private UserEntity requireAdmin(String authorization) {
        UserEntity user = authService.requireUser(authorization);
        authService.requireAnyRole(user, "ADMIN");
        return user;
    }
}
