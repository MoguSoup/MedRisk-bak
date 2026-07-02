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
@Table(name = "knowledge_documents")
public class KnowledgeDocumentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String title;
    @Column(nullable = false)
    private String originalFileName;
    @Column(nullable = false, length = 30)
    private String fileType;
    @Column(nullable = false)
    private Long fileSize;
    @Column(nullable = false, length = 80)
    private String fileBucket;
    @Column(nullable = false, length = 500)
    private String fileObjectKey;
    @Lob
    private String content;
    @Lob
    private String summary;
    @Column(nullable = false, length = 40)
    private String graphStatus;
    @Column(length = 1000)
    private String graphError;
    @Column(nullable = false)
    private Long uploadedBy;
    private String userName;
    private String sourceName;
    @Column(length = 500)
    private String sourceUrl;
    private String sourceLicense;
    private String sourceRecordId;
    private LocalDateTime retrievedAt;
    @Column(nullable = false, length = 30)
    private String visibility = "PUBLIC";
    @Column(nullable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getFileBucket() { return fileBucket; }
    public void setFileBucket(String fileBucket) { this.fileBucket = fileBucket; }
    public String getFileObjectKey() { return fileObjectKey; }
    public void setFileObjectKey(String fileObjectKey) { this.fileObjectKey = fileObjectKey; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getGraphStatus() { return graphStatus; }
    public void setGraphStatus(String graphStatus) { this.graphStatus = graphStatus; }
    public String getGraphError() { return graphError; }
    public void setGraphError(String graphError) { this.graphError = graphError; }
    public Long getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(Long uploadedBy) { this.uploadedBy = uploadedBy; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getSourceLicense() { return sourceLicense; }
    public void setSourceLicense(String sourceLicense) { this.sourceLicense = sourceLicense; }
    public String getSourceRecordId() { return sourceRecordId; }
    public void setSourceRecordId(String sourceRecordId) { this.sourceRecordId = sourceRecordId; }
    public LocalDateTime getRetrievedAt() { return retrievedAt; }
    public void setRetrievedAt(LocalDateTime retrievedAt) { this.retrievedAt = retrievedAt; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
