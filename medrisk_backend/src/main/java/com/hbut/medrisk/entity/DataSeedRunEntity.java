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
@Table(name = "data_seed_runs")
public class DataSeedRunEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 120)
    private String seedKey;
    @Column(nullable = false, length = 40)
    private String status;
    @Column(nullable = false)
    private Integer diseaseCount = 0;
    @Column(nullable = false)
    private Integer documentCount = 0;
    @Column(nullable = false)
    private Integer caseCount = 0;
    @Column(nullable = false)
    private Integer datasetCount = 0;
    @Column(nullable = false)
    private Integer graphNodeCount = 0;
    @Column(nullable = false)
    private Integer graphRelationshipCount = 0;
    @Lob
    private String message;
    private Long startedBy;
    @Column(nullable = false)
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSeedKey() { return seedKey; }
    public void setSeedKey(String seedKey) { this.seedKey = seedKey; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getDiseaseCount() { return diseaseCount; }
    public void setDiseaseCount(Integer diseaseCount) { this.diseaseCount = diseaseCount; }
    public Integer getDocumentCount() { return documentCount; }
    public void setDocumentCount(Integer documentCount) { this.documentCount = documentCount; }
    public Integer getCaseCount() { return caseCount; }
    public void setCaseCount(Integer caseCount) { this.caseCount = caseCount; }
    public Integer getDatasetCount() { return datasetCount; }
    public void setDatasetCount(Integer datasetCount) { this.datasetCount = datasetCount; }
    public Integer getGraphNodeCount() { return graphNodeCount; }
    public void setGraphNodeCount(Integer graphNodeCount) { this.graphNodeCount = graphNodeCount; }
    public Integer getGraphRelationshipCount() { return graphRelationshipCount; }
    public void setGraphRelationshipCount(Integer graphRelationshipCount) { this.graphRelationshipCount = graphRelationshipCount; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Long getStartedBy() { return startedBy; }
    public void setStartedBy(Long startedBy) { this.startedBy = startedBy; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
}
