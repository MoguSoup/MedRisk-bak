package com.hbut.medrisk.controller;

import com.hbut.medrisk.dto.ApiResponse;
import com.hbut.medrisk.dto.AuthSessionResponse;
import com.hbut.medrisk.dto.CurrentUserPasswordRequest;
import com.hbut.medrisk.dto.CurrentUserResponse;
import com.hbut.medrisk.dto.CurrentUserUpdateRequest;
import com.hbut.medrisk.dto.LoginRequest;
import com.hbut.medrisk.dto.RegisterRequest;
import com.hbut.medrisk.entity.UserEntity;
import com.hbut.medrisk.service.AuditService;
import com.hbut.medrisk.service.AuthService;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final AuditService auditService;

    public AuthController(AuthService authService, AuditService auditService) {
        this.authService = authService;
        this.auditService = auditService;
    }

    @PostMapping("/register")
    ApiResponse<AuthSessionResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/login")
    ApiResponse<AuthSessionResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @GetMapping("/me")
    ApiResponse<CurrentUserResponse> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        UserEntity user = authService.requireUser(authorization);
        return ApiResponse.ok(authService.toCurrentUser(user));
    }

    @PutMapping("/me")
    ApiResponse<CurrentUserResponse> updateMe(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody CurrentUserUpdateRequest request) {
        UserEntity user = authService.requireUser(authorization);
        return ApiResponse.ok(authService.updateCurrentUser(user, request));
    }

    @PostMapping("/me/password")
    ApiResponse<CurrentUserResponse> changePassword(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody CurrentUserPasswordRequest request) {
        UserEntity user = authService.requireUser(authorization);
        return ApiResponse.ok(authService.changePassword(user, request));
    }

    @PostMapping("/me/avatar")
    ApiResponse<CurrentUserResponse> updateAvatar(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestPart("avatar") MultipartFile avatar) throws IOException {
        UserEntity user = authService.requireUser(authorization);
        return ApiResponse.ok(authService.updateAvatar(user, avatar));
    }

    @PostMapping("/logout")
    ApiResponse<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        UserEntity user = authService.requireUser(authorization);
        auditService.log(user.getId(), "LOGOUT", "USER", user.getId().toString(), "{}");
        return ApiResponse.ok(null);
    }
}
