package com.hbut.medrisk.controller;

import com.hbut.medrisk.dto.ApiResponse;
import com.hbut.medrisk.entity.UserEntity;
import com.hbut.medrisk.service.AuthService;
import com.hbut.medrisk.service.KnowledgeGraphService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/knowledge-graph")
public class KnowledgeGraphController {
    private final AuthService authService;
    private final KnowledgeGraphService graph;

    public KnowledgeGraphController(AuthService authService, KnowledgeGraphService graph) {
        this.authService = authService;
        this.graph = graph;
    }

    @GetMapping("/health")
    ApiResponse<Map<String, Object>> health(@RequestHeader("Authorization") String authorization) {
        admin(authorization);
        return ApiResponse.ok(graph.health());
    }

    @PostMapping("/rebuild")
    ApiResponse<Map<String, Object>> rebuild(@RequestHeader("Authorization") String authorization) {
        return ApiResponse.ok(graph.rebuild(admin(authorization)));
    }

    @PostMapping("/incremental")
    ApiResponse<Map<String, Object>> incremental(@RequestHeader("Authorization") String authorization) {
        return ApiResponse.ok(graph.incremental(admin(authorization)));
    }

    @PostMapping("/sync/{documentId}")
    ApiResponse<Map<String, Object>> sync(@RequestHeader("Authorization") String authorization, @PathVariable Long documentId) {
        return ApiResponse.ok(graph.syncDocument(documentId, admin(authorization)));
    }

    @GetMapping("/jobs")
    ApiResponse<List<Map<String, Object>>> jobs(@RequestHeader("Authorization") String authorization) {
        admin(authorization);
        return ApiResponse.ok(graph.jobs());
    }

    @GetMapping("/search")
    ApiResponse<List<Map<String, Object>>> search(@RequestHeader("Authorization") String authorization, @RequestParam String keyword) {
        admin(authorization);
        return ApiResponse.ok(graph.search(keyword));
    }

    @GetMapping("/visualization")
    ApiResponse<Map<String, Object>> visualization(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) List<String> nodeTypes,
            @RequestParam(required = false) List<String> relationshipTypes,
            @RequestParam(required = false) String sourceName,
            @RequestParam(required = false) String visibility,
            @RequestParam(defaultValue = "120") int limit) {
        UserEntity user = admin(authorization);
        return ApiResponse.ok(graph.visualization(keyword, nodeTypes, relationshipTypes, sourceName, visibility, limit, user));
    }

    private UserEntity admin(String authorization) {
        UserEntity user = authService.requireUser(authorization);
        authService.requireAnyRole(user, "ADMIN");
        return user;
    }
}
