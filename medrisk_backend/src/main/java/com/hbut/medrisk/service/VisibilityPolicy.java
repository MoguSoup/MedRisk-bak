package com.hbut.medrisk.service;

import com.hbut.medrisk.entity.UserEntity;
import java.util.Locale;
import java.util.Set;

final class VisibilityPolicy {
    static final String PUBLIC = "PUBLIC";
    static final String DOCTOR_ONLY = "DOCTOR_ONLY";
    static final String ADMIN_ONLY = "ADMIN_ONLY";
    static final String DRAFT = "DRAFT";

    private VisibilityPolicy() {
    }

    static String normalize(String value, String fallback) {
        String normalized = value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case PUBLIC, DOCTOR_ONLY, ADMIN_ONLY, DRAFT -> normalized;
            default -> fallback;
        };
    }

    static Set<String> allowed(UserEntity user) {
        if (user == null) {
            return Set.of(PUBLIC);
        }
        return switch (user.getRole()) {
            case "ADMIN" -> Set.of(PUBLIC, DOCTOR_ONLY, ADMIN_ONLY, DRAFT);
            case "DOCTOR" -> Set.of(PUBLIC, DOCTOR_ONLY, DRAFT);
            default -> Set.of(PUBLIC);
        };
    }

    static boolean canRead(String visibility, Long ownerId, UserEntity user) {
        String normalized = normalize(visibility, PUBLIC);
        if (user != null && "ADMIN".equals(user.getRole())) {
            return true;
        }
        if (DRAFT.equals(normalized)) {
            return user != null && "DOCTOR".equals(user.getRole()) && ownerId != null && ownerId.equals(user.getId());
        }
        return allowed(user).contains(normalized);
    }

    static String display(String visibility) {
        return switch (normalize(visibility, PUBLIC)) {
            case DOCTOR_ONLY -> "医生专用";
            case ADMIN_ONLY -> "管理员";
            case DRAFT -> "草稿";
            default -> "公开";
        };
    }
}
