package com.hbut.medrisk.service;

import com.hbut.medrisk.dto.LlmProfileRequest;
import com.hbut.medrisk.dto.LlmProfileResponse;
import com.hbut.medrisk.entity.LlmModelProfileEntity;
import com.hbut.medrisk.entity.UserEntity;
import com.hbut.medrisk.repository.LlmModelProfileRepository;
import jakarta.persistence.EntityNotFoundException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LlmProfileService {
    public record RuntimeProfile(
            Long id,
            String displayName,
            String provider,
            String baseUrl,
            String modelName,
            String apiKey,
            boolean reasoningSupported,
            String reasoningProtocol) {
    }

    private final LlmModelProfileRepository profiles;
    private final AuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final byte[] secretKey;
    private final String envBaseUrl;
    private final String envApiKey;
    private final String envModel;

    public LlmProfileService(
            LlmModelProfileRepository profiles,
            AuditService auditService,
            @Value("${medrisk.jwt-secret}") String jwtSecret,
            @Value("${medrisk.llm.base-url}") String envBaseUrl,
            @Value("${medrisk.llm.api-key}") String envApiKey,
            @Value("${medrisk.llm.model}") String envModel) {
        this.profiles = profiles;
        this.auditService = auditService;
        this.secretKey = sha256(jwtSecret == null || jwtSecret.isBlank() ? "medrisk-local-secret" : jwtSecret);
        this.envBaseUrl = trimTrailingSlash(envBaseUrl);
        this.envApiKey = envApiKey == null ? "" : envApiKey.trim();
        this.envModel = envModel == null || envModel.isBlank() ? "qwen-plus" : envModel.trim();
    }

    @Transactional
    public List<LlmProfileResponse> publicProfiles() {
        ensureEnvProfile();
        return profiles.findByEnabledTrueOrderByDefaultProfileDescUpdatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public List<LlmProfileResponse> adminProfiles() {
        ensureEnvProfile();
        return profiles.findAllByOrderByDefaultProfileDescUpdatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public LlmProfileResponse create(LlmProfileRequest request, UserEntity user) {
        ensureEnvProfile();
        LlmModelProfileEntity row = new LlmModelProfileEntity();
        apply(row, request, true);
        profiles.save(row);
        if (Boolean.TRUE.equals(row.getDefaultProfile())) {
            clearOtherDefaults(row.getId());
        }
        auditService.log(user.getId(), "CREATE_LLM_PROFILE", "LLM_PROFILE", row.getId().toString(), "{}");
        return toResponse(row);
    }

    @Transactional
    public LlmProfileResponse update(Long id, LlmProfileRequest request, UserEntity user) {
        ensureEnvProfile();
        LlmModelProfileEntity row = profiles.findById(id).orElseThrow(() -> new EntityNotFoundException("大模型配置不存在"));
        apply(row, request, false);
        profiles.save(row);
        if (Boolean.TRUE.equals(row.getDefaultProfile())) {
            clearOtherDefaults(row.getId());
        }
        auditService.log(user.getId(), "UPDATE_LLM_PROFILE", "LLM_PROFILE", row.getId().toString(), "{}");
        return toResponse(row);
    }

    @Transactional
    public void disable(Long id, UserEntity user) {
        ensureEnvProfile();
        LlmModelProfileEntity row = profiles.findById(id).orElseThrow(() -> new EntityNotFoundException("大模型配置不存在"));
        row.setEnabled(false);
        row.setDefaultProfile(false);
        row.setUpdatedAt(LocalDateTime.now());
        profiles.save(row);
        auditService.log(user.getId(), "DISABLE_LLM_PROFILE", "LLM_PROFILE", row.getId().toString(), "{}");
    }

    @Transactional
    public RuntimeProfile resolve(Long id) {
        ensureEnvProfile();
        LlmModelProfileEntity row = id == null
                ? profiles.findFirstByDefaultProfileTrueAndEnabledTrueOrderByUpdatedAtDesc()
                        .orElseGet(() -> profiles.findByEnabledTrueOrderByDefaultProfileDescUpdatedAtDesc().stream().findFirst().orElse(null))
                : profiles.findById(id).filter(item -> Boolean.TRUE.equals(item.getEnabled())).orElse(null);
        if (row == null) {
            return new RuntimeProfile(null, envModel, providerName(envBaseUrl), envBaseUrl, envModel, envApiKey, false, "none");
        }
        return new RuntimeProfile(
                row.getId(),
                row.getDisplayName(),
                row.getProvider(),
                trimTrailingSlash(row.getBaseUrl()),
                row.getModelName(),
                decrypt(row.getApiKeyCipher()),
                Boolean.TRUE.equals(row.getReasoningSupported()),
                normalizeReasoningProtocol(row.getReasoningProtocol(), row.getProvider(), row.getBaseUrl()));
    }

    private void apply(LlmModelProfileEntity row, LlmProfileRequest request, boolean requireApiKey) {
        String displayName = clean(request.displayName());
        String baseUrl = trimTrailingSlash(request.baseUrl());
        String modelName = clean(request.modelName());
        if (displayName.isBlank()) throw new IllegalArgumentException("模型显示名不能为空");
        if (baseUrl.isBlank()) throw new IllegalArgumentException("模型 Base URL 不能为空");
        if (modelName.isBlank()) throw new IllegalArgumentException("模型名称不能为空");
        String apiKey = clean(request.apiKey());
        if (requireApiKey && apiKey.isBlank()) throw new IllegalArgumentException("API Key 不能为空");
        row.setDisplayName(displayName);
        row.setBaseUrl(baseUrl);
        row.setModelName(modelName);
        row.setProvider(clean(request.provider()).isBlank() ? providerName(baseUrl) : clean(request.provider()));
        if (!apiKey.isBlank()) {
            row.setApiKeyCipher(encrypt(apiKey));
        }
        row.setReasoningSupported(Boolean.TRUE.equals(request.reasoningSupported()));
        row.setReasoningProtocol(normalizeReasoningProtocol(request.reasoningProtocol(), row.getProvider(), baseUrl));
        row.setEnabled(request.enabled() == null || Boolean.TRUE.equals(request.enabled()));
        row.setDefaultProfile(Boolean.TRUE.equals(request.defaultProfile()));
        LocalDateTime now = LocalDateTime.now();
        if (row.getCreatedAt() == null) row.setCreatedAt(now);
        row.setUpdatedAt(now);
    }

    private void clearOtherDefaults(Long keepId) {
        for (LlmModelProfileEntity item : profiles.findAll()) {
            if (!item.getId().equals(keepId) && Boolean.TRUE.equals(item.getDefaultProfile())) {
                item.setDefaultProfile(false);
                item.setUpdatedAt(LocalDateTime.now());
                profiles.save(item);
            }
        }
    }

    private void ensureEnvProfile() {
        if (envApiKey.isBlank() || profiles.existsByModelNameAndBaseUrl(envModel, envBaseUrl)) {
            return;
        }
        LlmModelProfileEntity row = new LlmModelProfileEntity();
        row.setDisplayName(envModel + "（环境默认）");
        row.setProvider(providerName(envBaseUrl));
        row.setBaseUrl(envBaseUrl);
        row.setModelName(envModel);
        row.setApiKeyCipher(encrypt(envApiKey));
        row.setReasoningSupported(true);
        row.setReasoningProtocol(normalizeReasoningProtocol(null, row.getProvider(), envBaseUrl));
        row.setEnabled(true);
        row.setDefaultProfile(profiles.findByEnabledTrueOrderByDefaultProfileDescUpdatedAtDesc().isEmpty());
        LocalDateTime now = LocalDateTime.now();
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        profiles.save(row);
    }

    private LlmProfileResponse toResponse(LlmModelProfileEntity row) {
        String apiKey = decrypt(row.getApiKeyCipher());
        return new LlmProfileResponse(
                row.getId(),
                row.getDisplayName(),
                row.getProvider(),
                row.getBaseUrl(),
                row.getModelName(),
                mask(apiKey),
                !apiKey.isBlank(),
                Boolean.TRUE.equals(row.getReasoningSupported()),
                row.getReasoningProtocol(),
                Boolean.TRUE.equals(row.getEnabled()),
                Boolean.TRUE.equals(row.getDefaultProfile()),
                row.getCreatedAt(),
                row.getUpdatedAt());
    }

    private String encrypt(String plainText) {
        try {
            byte[] iv = new byte[12];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(secretKey, "AES"), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return "v1:" + Base64.getEncoder().encodeToString(iv) + ":" + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception ex) {
            throw new IllegalStateException("API Key 加密失败");
        }
    }

    private String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isBlank()) return "";
        try {
            String[] parts = cipherText.split(":", 3);
            if (parts.length != 3 || !"v1".equals(parts[0])) return "";
            byte[] iv = Base64.getDecoder().decode(parts[1]);
            byte[] encrypted = Base64.getDecoder().decode(parts[2]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(secretKey, "AES"), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return "";
        }
    }

    private String mask(String value) {
        if (value == null || value.isBlank()) return "";
        if (value.length() <= 10) return "****";
        return value.substring(0, 6) + "****" + value.substring(value.length() - 4);
    }

    private String providerName(String baseUrl) {
        String value = baseUrl == null ? "" : baseUrl.toLowerCase();
        if (value.contains("dashscope") || value.contains("aliyun") || value.contains("maas.aliyuncs")) return "dashscope";
        if (value.contains("deepseek")) return "deepseek";
        if (value.contains("openai")) return "openai-compatible";
        if (value.contains("huatuo")) return "huatuogpt-compatible";
        return "openai-compatible";
    }

    private String normalizeReasoningProtocol(String value, String provider, String baseUrl) {
        String normalized = clean(value).toLowerCase();
        if (List.of("bailian", "deepseek", "openai", "none").contains(normalized)) {
            return normalized;
        }
        String source = (clean(provider) + " " + clean(baseUrl)).toLowerCase();
        if (source.contains("dashscope") || source.contains("aliyun") || source.contains("maas.aliyuncs")) return "bailian";
        if (source.contains("deepseek")) return "deepseek";
        return "none";
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimTrailingSlash(String value) {
        String cleaned = clean(value);
        return cleaned.endsWith("/") ? cleaned.substring(0, cleaned.length() - 1) : cleaned;
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return ByteBuffer.allocate(32).putLong(value.hashCode()).array();
        }
    }
}
