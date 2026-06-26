package com.hbut.medrisk.controller;

import com.hbut.medrisk.dto.ApiResponse;
import com.hbut.medrisk.entity.UserEntity;
import com.hbut.medrisk.service.AuthService;
import com.hbut.medrisk.service.ConversationService;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {
    private final AuthService authService;
    private final ConversationService conversations;

    public ConversationController(AuthService authService, ConversationService conversations) {
        this.authService = authService;
        this.conversations = conversations;
    }

    @GetMapping
    ApiResponse<List<Map<String, Object>>> list(@RequestHeader("Authorization") String authorization) {
        return ApiResponse.ok(conversations.list(authService.requireUser(authorization)));
    }

    @PostMapping
    ApiResponse<Map<String, Object>> create(@RequestHeader("Authorization") String authorization, @RequestBody(required = false) Map<String, String> body) {
        return ApiResponse.ok(conversations.create(body == null ? "" : body.get("title"), authService.requireUser(authorization)));
    }

    @GetMapping("/{id}")
    ApiResponse<Map<String, Object>> get(@RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        return ApiResponse.ok(conversations.get(id, authService.requireUser(authorization)));
    }

    @DeleteMapping("/{id}")
    ApiResponse<Map<String, Object>> delete(@RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        return ApiResponse.ok(conversations.delete(id, authService.requireUser(authorization)));
    }

    @PostMapping(value = "/{id}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<Map<String, Object>> ask(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long id,
            @RequestParam String question,
            @RequestParam(required = false) MultipartFile image) throws IOException {
        return ApiResponse.ok(conversations.ask(id, question, image, authService.requireUser(authorization)));
    }
}
