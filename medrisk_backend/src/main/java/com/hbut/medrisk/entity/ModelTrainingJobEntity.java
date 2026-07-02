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
@Table(name = "model_training_jobs")
public class ModelTrainingJobEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long datasetId;
    private Long evaluationDatasetId;
    @Column(nullable = false)
    private Long userId;
    @Column(nullable = false, length = 40)
    private String diseaseType;
    @Column(nullable = false, length = 120)
    private String modelName;
    @Column(nullable = false, length = 30)
    private String modelType = "xgboost";
    @Column(nullable = false, length = 40)
    private String trainStatus;
    @Column(nullable = false)
    private Integer progress;
    private Double currentLoss;
    @Column(nullable = false)
    private Integer trainEpoch;
    @Column(nullable = false)
    private Double learningRate;
    @Column(nullable = false)
    private Double testSize;
    @Lob
    private String hyperparametersJson;
    private String modelVersion;
    @Column(length = 500)
    private String modelPath;
    @Column(length = 500)
    private String historyPath;
    @Column(length = 500)
    private String metadataPath;
    @Lob
    private String metricsJson;
    @Lob
    private String message;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDatasetId() { return datasetId; }
    public void setDatasetId(Long datasetId) { this.datasetId = datasetId; }
    public Long getEvaluationDatasetId() { return evaluationDatasetId; }
    public void setEvaluationDatasetId(Long evaluationDatasetId) { this.evaluationDatasetId = evaluationDatasetId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getDiseaseType() { return diseaseType; }
    public void setDiseaseType(String diseaseType) { this.diseaseType = diseaseType; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getModelType() { return modelType; }
    public void setModelType(String modelType) { this.modelType = modelType; }
    public String getTrainStatus() { return trainStatus; }
    public void setTrainStatus(String trainStatus) { this.trainStatus = trainStatus; }
    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress; }
    public Double getCurrentLoss() { return currentLoss; }
    public void setCurrentLoss(Double currentLoss) { this.currentLoss = currentLoss; }
    public Integer getTrainEpoch() { return trainEpoch; }
    public void setTrainEpoch(Integer trainEpoch) { this.trainEpoch = trainEpoch; }
    public Double getLearningRate() { return learningRate; }
    public void setLearningRate(Double learningRate) { this.learningRate = learningRate; }
    public Double getTestSize() { return testSize; }
    public void setTestSize(Double testSize) { this.testSize = testSize; }
    public String getHyperparametersJson() { return hyperparametersJson; }
    public void setHyperparametersJson(String hyperparametersJson) { this.hyperparametersJson = hyperparametersJson; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public String getModelPath() { return modelPath; }
    public void setModelPath(String modelPath) { this.modelPath = modelPath; }
    public String getHistoryPath() { return historyPath; }
    public void setHistoryPath(String historyPath) { this.historyPath = historyPath; }
    public String getMetadataPath() { return metadataPath; }
    public void setMetadataPath(String metadataPath) { this.metadataPath = metadataPath; }
    public String getMetricsJson() { return metricsJson; }
    public void setMetricsJson(String metricsJson) { this.metricsJson = metricsJson; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
