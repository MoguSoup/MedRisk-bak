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
@Table(name = "knowledge_graph_jobs")
public class KnowledgeGraphJobEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 40)
    private String jobType;
    @Column(nullable = false, length = 40)
    private String status;
    @Column(nullable = false)
    private Integer progress;
    @Lob
    private String message;
    @Column(nullable = false)
    private Integer nodesCreated;
    @Column(nullable = false)
    private Integer relationshipsCreated;
    private Long documentId;
    @Column(nullable = false)
    private Long startedBy;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getJobType() { return jobType; }
    public void setJobType(String jobType) { this.jobType = jobType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Integer getNodesCreated() { return nodesCreated; }
    public void setNodesCreated(Integer nodesCreated) { this.nodesCreated = nodesCreated; }
    public Integer getRelationshipsCreated() { return relationshipsCreated; }
    public void setRelationshipsCreated(Integer relationshipsCreated) { this.relationshipsCreated = relationshipsCreated; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public Long getStartedBy() { return startedBy; }
    public void setStartedBy(Long startedBy) { this.startedBy = startedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
