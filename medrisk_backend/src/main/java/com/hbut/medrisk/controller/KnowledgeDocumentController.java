package com.hbut.medrisk.controller;

import com.hbut.medrisk.dto.ApiResponse;
import com.hbut.medrisk.entity.UserEntity;
import com.hbut.medrisk.service.AuthService;
import com.hbut.medrisk.service.FileStorageService;
import com.hbut.medrisk.service.KnowledgeDocumentService;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class KnowledgeDocumentController {
    private final AuthService authService;
    private final KnowledgeDocumentService documents;

    public KnowledgeDocumentController(AuthService authService, KnowledgeDocumentService documents) {
        this.authService = authService;
        this.documents = documents;
    }

    @GetMapping("/api/documents")
    ApiResponse<List<Map<String, Object>>> list(@RequestHeader("Authorization") String authorization, @RequestParam(required = false) String keyword) {
        UserEntity user = authService.requireUser(authorization);
        authService.requireAnyRole(user, "DOCTOR", "ADMIN");
        return ApiResponse.ok(documents.list(keyword, user));
    }

    @GetMapping("/api/documents/{id}")
    ApiResponse<Map<String, Object>> get(@RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        UserEntity user = authService.requireUser(authorization);
        authService.requireAnyRole(user, "DOCTOR", "ADMIN");
        return ApiResponse.ok(documents.get(id, user));
    }

    @GetMapping("/api/documents/{id}/download")
    ResponseEntity<?> download(@RequestHeader("Authorization") String authorization, @PathVariable Long id) throws IOException {
        UserEntity user = authService.requireUser(authorization);
        authService.requireAnyRole(user, "DOCTOR", "ADMIN");
        FileStorageService.StoredResource resource = documents.download(id, user);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(resource.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.filename() + "\"")
                .body(resource.resource());
    }

    @PostMapping(value = "/api/admin/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<Map<String, Object>> upload(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String visibility,
            @RequestParam MultipartFile file) throws IOException {
        UserEntity user = authService.requireUser(authorization);
        authService.requireAnyRole(user, "ADMIN");
        return ApiResponse.ok(documents.upload(title, file, user, visibility == null ? "PUBLIC" : visibility));
    }

    @PostMapping(value = "/api/doctor/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<Map<String, Object>> doctorUpload(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(required = false) String title,
            @RequestParam MultipartFile file) throws IOException {
        UserEntity user = authService.requireUser(authorization);
        authService.requireAnyRole(user, "DOCTOR");
        return ApiResponse.ok(documents.upload(title, file, user, "DRAFT"));
    }

    @PutMapping("/api/admin/documents/{id}")
    ApiResponse<Map<String, Object>> update(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long id,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String summary,
            @RequestParam(required = false) String visibility,
            @RequestParam(required = false) String sourceName,
            @RequestParam(required = false) String sourceUrl,
            @RequestParam(required = false) String sourceLicense) {
        UserEntity user = authService.requireUser(authorization);
        authService.requireAnyRole(user, "ADMIN");
        return ApiResponse.ok(documents.update(id, title, summary, visibility, sourceName, sourceUrl, sourceLicense, user));
    }

    @DeleteMapping("/api/admin/documents/{id}")
    ApiResponse<Map<String, Object>> delete(@RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        UserEntity user = authService.requireUser(authorization);
        authService.requireAnyRole(user, "ADMIN");
        return ApiResponse.ok(documents.delete(id, user));
    }
}
