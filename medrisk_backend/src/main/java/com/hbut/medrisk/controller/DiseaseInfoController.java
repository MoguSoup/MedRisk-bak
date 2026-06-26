package com.hbut.medrisk.controller;

import com.hbut.medrisk.dto.ApiResponse;
import com.hbut.medrisk.entity.UserEntity;
import com.hbut.medrisk.service.AuthService;
import com.hbut.medrisk.service.DiseaseInfoService;
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
public class DiseaseInfoController {
    private final AuthService authService;
    private final DiseaseInfoService diseases;

    public DiseaseInfoController(AuthService authService, DiseaseInfoService diseases) {
        this.authService = authService;
        this.diseases = diseases;
    }

    @GetMapping("/api/diseases")
    ApiResponse<List<Map<String, Object>>> list(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String department) {
        UserEntity user = authService.requireUser(authorization);
        authService.requireAnyRole(user, "DOCTOR", "ADMIN");
        return ApiResponse.ok(diseases.list(keyword, department, user));
    }

    @GetMapping("/api/diseases/{id}")
    ApiResponse<Map<String, Object>> get(@RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        UserEntity user = authService.requireUser(authorization);
        authService.requireAnyRole(user, "DOCTOR", "ADMIN");
        return ApiResponse.ok(diseases.get(id, user));
    }

    @PostMapping(value = "/api/admin/diseases", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<Map<String, Object>> create(
            @RequestHeader("Authorization") String authorization,
            @RequestParam Map<String, String> fields,
            @RequestParam(required = false) MultipartFile image) throws IOException {
        return ApiResponse.ok(diseases.create(fields, image, admin(authorization)));
    }

    @PostMapping(value = "/api/doctor/diseases", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<Map<String, Object>> doctorCreate(
            @RequestHeader("Authorization") String authorization,
            @RequestParam Map<String, String> fields,
            @RequestParam(required = false) MultipartFile image) throws IOException {
        UserEntity user = authService.requireUser(authorization);
        authService.requireAnyRole(user, "DOCTOR");
        fields.put("visibility", "DRAFT");
        return ApiResponse.ok(diseases.create(fields, image, user));
    }

    @PutMapping(value = "/api/admin/diseases/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<Map<String, Object>> update(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long id,
            @RequestParam Map<String, String> fields,
            @RequestParam(required = false) MultipartFile image) throws IOException {
        return ApiResponse.ok(diseases.update(id, fields, image, admin(authorization)));
    }

    @DeleteMapping("/api/admin/diseases/{id}")
    ApiResponse<Map<String, Object>> delete(@RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        return ApiResponse.ok(diseases.delete(id, admin(authorization)));
    }

    private UserEntity admin(String authorization) {
        UserEntity user = authService.requireUser(authorization);
        authService.requireAnyRole(user, "ADMIN");
        return user;
    }
}
