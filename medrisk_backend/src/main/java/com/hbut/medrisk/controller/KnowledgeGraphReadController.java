package com.hbut.medrisk.controller;

import com.hbut.medrisk.dto.ApiResponse;
import com.hbut.medrisk.entity.UserEntity;
import com.hbut.medrisk.service.AuthService;
import com.hbut.medrisk.service.KnowledgeGraphService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class KnowledgeGraphReadController {
    private final AuthService authService;
    private final KnowledgeGraphService graph;

    public KnowledgeGraphReadController(AuthService authService, KnowledgeGraphService graph) {
        this.authService = authService;
        this.graph = graph;
    }

    @GetMapping("/api/knowledge-graph/visualization")
    ApiResponse<Map<String, Object>> visualization(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) List<String> nodeTypes,
            @RequestParam(required = false) List<String> relationshipTypes,
            @RequestParam(required = false) String sourceName,
            @RequestParam(required = false) String visibility,
            @RequestParam(defaultValue = "120") int limit) {
        UserEntity user = authService.requireUser(authorization);
        authService.requireAnyRole(user, "DOCTOR", "ADMIN");
        return ApiResponse.ok(graph.visualization(keyword, nodeTypes, relationshipTypes, sourceName, visibility, limit, user));
    }
}
