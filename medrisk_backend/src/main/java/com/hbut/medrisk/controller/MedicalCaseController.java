package com.hbut.medrisk.controller;

import com.hbut.medrisk.dto.ApiResponse;
import com.hbut.medrisk.entity.UserEntity;
import com.hbut.medrisk.service.AuthService;
import com.hbut.medrisk.service.MedicalCaseService;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class MedicalCaseController {
    private final AuthService authService;
    private final MedicalCaseService cases;

    public MedicalCaseController(AuthService authService, MedicalCaseService cases) {
        this.authService = authService;
        this.cases = cases;
    }

    @GetMapping("/api/medical-cases")
    ApiResponse<List<Map<String, Object>>> list(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long diseaseId,
            @RequestParam(required = false) String hospital) {
        UserEntity user = authService.requireUser(authorization);
        authService.requireAnyRole(user, "DOCTOR", "ADMIN");
        return ApiResponse.ok(cases.list(keyword, diseaseId, hospital, user));
    }

    @GetMapping("/api/medical-cases/{id}")
    ApiResponse<Map<String, Object>> get(@RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        UserEntity user = authService.requireUser(authorization);
        authService.requireAnyRole(user, "DOCTOR", "ADMIN");
        return ApiResponse.ok(cases.get(id, user));
    }

    @PostMapping(value = "/api/admin/medical-cases", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<Map<String, Object>> create(
            @RequestHeader("Authorization") String authorization,
            @RequestParam Map<String, String> fields,
            @RequestParam(required = false) List<MultipartFile> images) throws IOException {
        return ApiResponse.ok(cases.create(fields, images, admin(authorization)));
    }

    @PostMapping(value = "/api/doctor/medical-cases", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<Map<String, Object>> doctorCreate(
            @RequestHeader("Authorization") String authorization,
            @RequestParam Map<String, String> fields,
            @RequestParam(required = false) List<MultipartFile> images) throws IOException {
        UserEntity user = authService.requireUser(authorization);
        authService.requireAnyRole(user, "DOCTOR");
        fields.put("visibility", "DRAFT");
        fields.put("syntheticCase", "false");
        return ApiResponse.ok(cases.create(fields, images, user));
    }

    @PutMapping(value = "/api/admin/medical-cases/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<Map<String, Object>> update(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long id,
            @RequestParam Map<String, String> fields,
            @RequestParam(required = false) List<MultipartFile> images) throws IOException {
        return ApiResponse.ok(cases.update(id, fields, images, admin(authorization)));
    }

    @DeleteMapping("/api/admin/medical-cases/{id}")
    ApiResponse<Map<String, Object>> delete(@RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        return ApiResponse.ok(cases.delete(id, admin(authorization)));
    }

    private UserEntity admin(String authorization) {
        UserEntity user = authService.requireUser(authorization);
        authService.requireAnyRole(user, "ADMIN");
        return user;
    }
}
