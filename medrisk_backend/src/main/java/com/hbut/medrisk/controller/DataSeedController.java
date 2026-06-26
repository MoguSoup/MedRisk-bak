package com.hbut.medrisk.controller;

import com.hbut.medrisk.dto.ApiResponse;
import com.hbut.medrisk.entity.UserEntity;
import com.hbut.medrisk.service.AuthService;
import com.hbut.medrisk.service.DataSeedService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/data-seeds")
public class DataSeedController {
    private final AuthService authService;
    private final DataSeedService dataSeeds;

    public DataSeedController(AuthService authService, DataSeedService dataSeeds) {
        this.authService = authService;
        this.dataSeeds = dataSeeds;
    }

    @GetMapping("/status")
    ApiResponse<Map<String, Object>> status(@RequestHeader("Authorization") String authorization) {
        admin(authorization);
        return ApiResponse.ok(dataSeeds.status());
    }

    @PostMapping("/import")
    ApiResponse<Map<String, Object>> importDemoPack(@RequestHeader("Authorization") String authorization) {
        return ApiResponse.ok(dataSeeds.importDemoPack(admin(authorization), false));
    }

    @PostMapping("/rebuild-demo-pack")
    ApiResponse<Map<String, Object>> rebuildDemoPack(@RequestHeader("Authorization") String authorization) {
        return ApiResponse.ok(dataSeeds.importDemoPack(admin(authorization), true));
    }

    private UserEntity admin(String authorization) {
        UserEntity user = authService.requireUser(authorization);
        authService.requireAnyRole(user, "ADMIN");
        return user;
    }
}
