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
@Table(name = "model_versions")
public class ModelVersionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 40)
    private String diseaseType;
    @Column(nullable = false, length = 40)
    private String diseaseName;
    @Column(nullable = false, length = 80)
    private String modelName;
    @Column(nullable = false, length = 30)
    private String modelType = "xgboost";
    @Column(nullable = false, unique = true, length = 120)
    private String version;
    @Lob
    @Column(nullable = false)
    private String metricsJson;
    @Lob
    private String featureSchemaJson;
    @Lob
    private String hyperparametersJson;
    private String evaluationDatasetName;
    private String evaluationDatasetSource;
    private String evaluationDatasetUrl;
    private String modelPath;
    @Column(nullable = false)
    private Boolean active;
    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDiseaseType() {
        return diseaseType;
    }

    public void setDiseaseType(String diseaseType) {
        this.diseaseType = diseaseType;
    }

    public String getDiseaseName() {
        return diseaseName;
    }

    public void setDiseaseName(String diseaseName) {
        this.diseaseName = diseaseName;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getModelType() {
        return modelType;
    }

    public void setModelType(String modelType) {
        this.modelType = modelType;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getMetricsJson() {
        return metricsJson;
    }

    public void setMetricsJson(String metricsJson) {
        this.metricsJson = metricsJson;
    }

    public String getFeatureSchemaJson() {
        return featureSchemaJson;
    }

    public void setFeatureSchemaJson(String featureSchemaJson) {
        this.featureSchemaJson = featureSchemaJson;
    }

    public String getHyperparametersJson() {
        return hyperparametersJson;
    }

    public void setHyperparametersJson(String hyperparametersJson) {
        this.hyperparametersJson = hyperparametersJson;
    }

    public String getEvaluationDatasetName() {
        return evaluationDatasetName;
    }

    public void setEvaluationDatasetName(String evaluationDatasetName) {
        this.evaluationDatasetName = evaluationDatasetName;
    }

    public String getEvaluationDatasetSource() {
        return evaluationDatasetSource;
    }

    public void setEvaluationDatasetSource(String evaluationDatasetSource) {
        this.evaluationDatasetSource = evaluationDatasetSource;
    }

    public String getEvaluationDatasetUrl() {
        return evaluationDatasetUrl;
    }

    public void setEvaluationDatasetUrl(String evaluationDatasetUrl) {
        this.evaluationDatasetUrl = evaluationDatasetUrl;
    }

    public String getModelPath() {
        return modelPath;
    }

    public void setModelPath(String modelPath) {
        this.modelPath = modelPath;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
