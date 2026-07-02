package com.hbut.medrisk.controller;

import com.hbut.medrisk.dto.ApiResponse;
import com.hbut.medrisk.entity.UserEntity;
import com.hbut.medrisk.service.AdminService;
import com.hbut.medrisk.service.AuthService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/doctor")
public class DoctorController {
    private final AuthService authService;
    private final AdminService adminService;

    public DoctorController(AuthService authService, AdminService adminService) {
        this.authService = authService;
        this.adminService = adminService;
    }

    @GetMapping("/console/summary")
    ApiResponse<Map<String, Object>> summary(@RequestHeader(value = "Authorization", required = false) String authorization) {
        UserEntity user = authService.requireUser(authorization);
        authService.requireAnyRole(user, "DOCTOR", "ADMIN");
        return ApiResponse.ok(adminService.doctorSummary());
    }
}
