package com.hbut.medrisk.config;

import com.hbut.medrisk.service.ClientIpResolver;
import com.hbut.medrisk.service.RequestIpContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ClientIpContextFilter extends OncePerRequestFilter {
    private final ClientIpResolver clientIpResolver;
    private final Set<String> ipBlacklist;

    public ClientIpContextFilter(
            ClientIpResolver clientIpResolver,
            @Value("${medrisk.security.ip-blacklist:}") String ipBlacklist) {
        this.clientIpResolver = clientIpResolver;
        this.ipBlacklist = Arrays.stream((ipBlacklist == null ? "" : ipBlacklist).split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String clientIp = clientIpResolver.resolve(request);
        if (clientIp != null && ipBlacklist.contains(clientIp)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"当前请求 IP 已被禁止访问\",\"data\":null}");
            return;
        }
        try {
            RequestIpContext.set(clientIp);
            filterChain.doFilter(request, response);
        } finally {
            RequestIpContext.clear();
        }
    }
}
