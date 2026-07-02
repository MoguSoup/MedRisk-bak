package com.hbut.medrisk.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "llm_model_profiles")
public class LlmModelProfileEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 120)
    private String displayName;
    @Column(nullable = false, length = 60)
    private String provider;
    @Column(nullable = false, length = 500)
    private String baseUrl;
    @Column(nullable = false, length = 160)
    private String modelName;
    @Lob
    private String apiKeyCipher;
    @Column(nullable = false)
    private Boolean reasoningSupported = false;
    @Column(nullable = false, length = 40)
    private String reasoningProtocol = "none";
    @Column(nullable = false)
    private Boolean enabled = true;
    @Column(nullable = false)
    private Boolean defaultProfile = false;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getApiKeyCipher() { return apiKeyCipher; }
    public void setApiKeyCipher(String apiKeyCipher) { this.apiKeyCipher = apiKeyCipher; }
    public Boolean getReasoningSupported() { return reasoningSupported; }
    public void setReasoningSupported(Boolean reasoningSupported) { this.reasoningSupported = reasoningSupported; }
    public String getReasoningProtocol() { return reasoningProtocol; }
    public void setReasoningProtocol(String reasoningProtocol) { this.reasoningProtocol = reasoningProtocol; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Boolean getDefaultProfile() { return defaultProfile; }
    public void setDefaultProfile(Boolean defaultProfile) { this.defaultProfile = defaultProfile; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
