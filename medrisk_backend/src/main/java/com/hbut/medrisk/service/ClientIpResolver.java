package com.hbut.medrisk.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ClientIpResolver {
    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwardedFor = firstUsable(request.getHeader("X-Forwarded-For"));
        if (forwardedFor != null) {
            return forwardedFor;
        }
        String realIp = firstUsable(request.getHeader("X-Real-IP"));
        if (realIp != null) {
            return realIp;
        }
        return firstUsable(request.getRemoteAddr());
    }

    private String firstUsable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (String part : value.split(",")) {
            String candidate = part.trim();
            if (!candidate.isBlank() && !"unknown".equalsIgnoreCase(candidate)) {
                return candidate.length() > 45 ? candidate.substring(0, 45) : candidate;
            }
        }
        return null;
    }
}
