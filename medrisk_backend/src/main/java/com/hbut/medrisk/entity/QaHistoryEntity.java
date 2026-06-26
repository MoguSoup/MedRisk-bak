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
@Table(name = "qa_history")
public class QaHistoryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long conversationId;
    @Lob
    @Column(nullable = false)
    private String question;
    @Lob
    private String answer;
    @Lob
    private String relatedEntitiesJson;
    @Lob
    private String graphContextJson;
    @Lob
    private String diseaseInfoMatchesJson;
    @Lob
    private String diseaseCaseMatchesJson;
    @Lob
    private String keywordsJson;
    private String imageBucket;
    @Column(length = 500)
    private String imageObjectKey;
    @Column(length = 700)
    private String imageUrl;
    @Column(nullable = false)
    private Long userId;
    private String userName;
    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public String getRelatedEntitiesJson() { return relatedEntitiesJson; }
    public void setRelatedEntitiesJson(String relatedEntitiesJson) { this.relatedEntitiesJson = relatedEntitiesJson; }
    public String getGraphContextJson() { return graphContextJson; }
    public void setGraphContextJson(String graphContextJson) { this.graphContextJson = graphContextJson; }
    public String getDiseaseInfoMatchesJson() { return diseaseInfoMatchesJson; }
    public void setDiseaseInfoMatchesJson(String diseaseInfoMatchesJson) { this.diseaseInfoMatchesJson = diseaseInfoMatchesJson; }
    public String getDiseaseCaseMatchesJson() { return diseaseCaseMatchesJson; }
    public void setDiseaseCaseMatchesJson(String diseaseCaseMatchesJson) { this.diseaseCaseMatchesJson = diseaseCaseMatchesJson; }
    public String getKeywordsJson() { return keywordsJson; }
    public void setKeywordsJson(String keywordsJson) { this.keywordsJson = keywordsJson; }
    public String getImageBucket() { return imageBucket; }
    public void setImageBucket(String imageBucket) { this.imageBucket = imageBucket; }
    public String getImageObjectKey() { return imageObjectKey; }
    public void setImageObjectKey(String imageObjectKey) { this.imageObjectKey = imageObjectKey; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
