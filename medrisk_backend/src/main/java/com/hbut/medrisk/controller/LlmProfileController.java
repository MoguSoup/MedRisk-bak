package com.hbut.medrisk.controller;

import com.hbut.medrisk.dto.ApiResponse;
import com.hbut.medrisk.dto.LlmProfileRequest;
import com.hbut.medrisk.dto.LlmProfileResponse;
import com.hbut.medrisk.entity.UserEntity;
import com.hbut.medrisk.service.AuthService;
import com.hbut.medrisk.service.LlmProfileService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LlmProfileController {
    private final AuthService authService;
    private final LlmProfileService profiles;

    public LlmProfileController(AuthService authService, LlmProfileService profiles) {
        this.authService = authService;
        this.profiles = profiles;
    }

    @GetMapping("/api/llm-profiles")
    ApiResponse<List<LlmProfileResponse>> publicProfiles(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.requireUser(authorization);
        return ApiResponse.ok(profiles.publicProfiles());
    }

    @GetMapping("/api/admin/llm-profiles")
    ApiResponse<List<LlmProfileResponse>> adminProfiles(@RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAdmin(authorization);
        return ApiResponse.ok(profiles.adminProfiles());
    }

    @PostMapping("/api/admin/llm-profiles")
    ApiResponse<LlmProfileResponse> create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody LlmProfileRequest request) {
        UserEntity user = requireAdmin(authorization);
        return ApiResponse.ok(profiles.create(request, user));
    }

    @PutMapping("/api/admin/llm-profiles/{id}")
    ApiResponse<LlmProfileResponse> update(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id,
            @RequestBody LlmProfileRequest request) {
        UserEntity user = requireAdmin(authorization);
        return ApiResponse.ok(profiles.update(id, request, user));
    }

    @DeleteMapping("/api/admin/llm-profiles/{id}")
    ApiResponse<Map<String, Object>> disable(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id) {
        UserEntity user = requireAdmin(authorization);
        profiles.disable(id, user);
        return ApiResponse.ok(Map.of("disabled", true, "id", id));
    }

    private UserEntity requireAdmin(String authorization) {
        UserEntity user = authService.requireUser(authorization);
        authService.requireAnyRole(user, "ADMIN");
        return user;
    }
}
