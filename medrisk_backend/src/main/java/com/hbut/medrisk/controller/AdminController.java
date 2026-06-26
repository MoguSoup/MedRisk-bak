package com.hbut.medrisk.controller;

import com.hbut.medrisk.dto.ApiResponse;
import com.hbut.medrisk.dto.AdminPasswordResetRequest;
import com.hbut.medrisk.dto.AdminUserRequest;
import com.hbut.medrisk.dto.AdminUserResponse;
import com.hbut.medrisk.dto.AuditLogResponse;
import com.hbut.medrisk.dto.ModelVersionResponse;
import com.hbut.medrisk.dto.UserStatusRequest;
import com.hbut.medrisk.entity.UserEntity;
import com.hbut.medrisk.service.AdminService;
import com.hbut.medrisk.service.AuthService;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AuthService authService;
    private final AdminService adminService;

    public AdminController(AuthService authService, AdminService adminService) {
        this.authService = authService;
        this.adminService = adminService;
    }

    @GetMapping("/models")
    ApiResponse<List<ModelVersionResponse>> models(@RequestHeader(value = "Authorization", required = false) String authorization) {
        UserEntity user = authService.requireUser(authorization);
        authService.requireAnyRole(user, "ADMIN");
        return ApiResponse.ok(adminService.models());
    }

    @GetMapping("/users")
    ApiResponse<List<AdminUserResponse>> users(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String role,
            @RequestParam(defaultValue = "") String status,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        UserEntity user = authService.requireUser(authorization);
        authService.requireAnyRole(user, "ADMIN");
        return ApiResponse.ok(adminService.users(keyword, role, status));
    }

    @PostMapping("/users")
    ApiResponse<AdminUserResponse> createUser(
            @Valid @RequestBody AdminUserRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        UserEntity user = authService.requireUser(authorization);
        authService.requireAnyRole(user, "ADMIN");
        return ApiResponse.ok(adminService.createUser(request, user));
    }

    @PutMapping("/users/{id}")
    ApiResponse<AdminUserResponse> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody AdminUserRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        UserEntity user = authService.requireUser(authorization);
        authService.requireAnyRole(user, "ADMIN");
        return ApiResponse.ok(adminService.updateUser(id, request, user));
    }

    @PostMapping("/users/{id}/status")
    ApiResponse<AdminUserResponse> updateUserStatus(
            @PathVariable Long id,
            @Valid @RequestBody UserStatusRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        UserEntity user = authService.requireUser(authorization);
        authService.requireAnyRole(user, "ADMIN");
        return ApiResponse.ok(adminService.updateStatus(id, request, user));
    }

    @PostMapping("/users/{id}/reset-password")
    ApiResponse<Map<String, Object>> resetPassword(
            @PathVariable Long id,
            @RequestBody(required = false) AdminPasswordResetRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        UserEntity user = authService.requireUser(authorization);
        authService.requireAnyRole(user, "ADMIN");
        return ApiResponse.ok(adminService.resetPassword(id, request, user));
    }

    @DeleteMapping("/users/{id}")
    ApiResponse<Map<String, Object>> deleteUser(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        UserEntity user = authService.requireUser(authorization);
        authService.requireAnyRole(user, "ADMIN");
        return ApiResponse.ok(adminService.deleteUser(id, user));
    }

    @GetMapping("/console/summary")
    ApiResponse<Map<String, Object>> consoleSummary(@RequestHeader(value = "Authorization", required = false) String authorization) {
        UserEntity user = authService.requireUser(authorization);
        authService.requireAnyRole(user, "ADMIN");
        return ApiResponse.ok(adminService.consoleSummary());
    }

    @GetMapping("/visualization")
    ApiResponse<Map<String, Object>> visualization(@RequestHeader(value = "Authorization", required = false) String authorization) {
        UserEntity user = authService.requireUser(authorization);
        authService.requireAnyRole(user, "ADMIN");
        return ApiResponse.ok(adminService.visualization());
    }

    @GetMapping("/audit-logs")
    ApiResponse<List<AuditLogResponse>> auditLogs(@RequestHeader(value = "Authorization", required = false) String authorization) {
        UserEntity user = authService.requireUser(authorization);
        authService.requireAnyRole(user, "ADMIN");
        return ApiResponse.ok(adminService.auditLogs());
    }
}
