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
@Table(name = "disease_info")
public class DiseaseInfoEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 50)
    private String diseaseCode;
    @Column(nullable = false, length = 100)
    private String diseaseName;
    private String diseaseNameEn;
    private String diseaseCategory;
    private String department;
    private String pathogen;
    @Lob
    private String symptoms;
    @Lob
    private String riskFactors;
    @Lob
    private String preventionMeasures;
    @Lob
    private String treatmentPlan;
    private String severityLevel;
    @Column(name = "is_infectious", nullable = false)
    private Boolean infectious;
    private String incubationPeriod;
    @Lob
    private String commonComplications;
    private String prognosis;
    @Lob
    private String description;
    private String imageBucket;
    @Column(length = 500)
    private String imageObjectKey;
    private String sourceName;
    @Column(length = 500)
    private String sourceUrl;
    private String sourceLicense;
    private String sourceRecordId;
    private LocalDateTime retrievedAt;
    @Column(nullable = false, length = 30)
    private String visibility = "PUBLIC";
    private Long createdBy;
    private String createdByName;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDiseaseCode() { return diseaseCode; }
    public void setDiseaseCode(String diseaseCode) { this.diseaseCode = diseaseCode; }
    public String getDiseaseName() { return diseaseName; }
    public void setDiseaseName(String diseaseName) { this.diseaseName = diseaseName; }
    public String getDiseaseNameEn() { return diseaseNameEn; }
    public void setDiseaseNameEn(String diseaseNameEn) { this.diseaseNameEn = diseaseNameEn; }
    public String getDiseaseCategory() { return diseaseCategory; }
    public void setDiseaseCategory(String diseaseCategory) { this.diseaseCategory = diseaseCategory; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getPathogen() { return pathogen; }
    public void setPathogen(String pathogen) { this.pathogen = pathogen; }
    public String getSymptoms() { return symptoms; }
    public void setSymptoms(String symptoms) { this.symptoms = symptoms; }
    public String getRiskFactors() { return riskFactors; }
    public void setRiskFactors(String riskFactors) { this.riskFactors = riskFactors; }
    public String getPreventionMeasures() { return preventionMeasures; }
    public void setPreventionMeasures(String preventionMeasures) { this.preventionMeasures = preventionMeasures; }
    public String getTreatmentPlan() { return treatmentPlan; }
    public void setTreatmentPlan(String treatmentPlan) { this.treatmentPlan = treatmentPlan; }
    public String getSeverityLevel() { return severityLevel; }
    public void setSeverityLevel(String severityLevel) { this.severityLevel = severityLevel; }
    public Boolean getInfectious() { return infectious; }
    public void setInfectious(Boolean infectious) { this.infectious = infectious; }
    public String getIncubationPeriod() { return incubationPeriod; }
    public void setIncubationPeriod(String incubationPeriod) { this.incubationPeriod = incubationPeriod; }
    public String getCommonComplications() { return commonComplications; }
    public void setCommonComplications(String commonComplications) { this.commonComplications = commonComplications; }
    public String getPrognosis() { return prognosis; }
    public void setPrognosis(String prognosis) { this.prognosis = prognosis; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getImageBucket() { return imageBucket; }
    public void setImageBucket(String imageBucket) { this.imageBucket = imageBucket; }
    public String getImageObjectKey() { return imageObjectKey; }
    public void setImageObjectKey(String imageObjectKey) { this.imageObjectKey = imageObjectKey; }
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
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
