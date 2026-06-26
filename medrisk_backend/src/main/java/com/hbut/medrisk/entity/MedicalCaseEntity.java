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
@Table(name = "medical_cases")
public class MedicalCaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long diseaseId;
    @Column(nullable = false)
    private String caseTitle;
    private LocalDateTime visitDate;
    private String hospital;
    private Integer patientAge;
    private String patientGender;
    private String affectedArea;
    private String severityLevel;
    @Lob
    private String chiefComplaint;
    @Lob
    private String presentIllness;
    @Lob
    private String pastHistory;
    @Lob
    private String physicalExamination;
    @Lob
    private String labResults;
    @Lob
    private String imagingResults;
    @Lob
    private String symptomDescription;
    @Lob
    private String diagnosis;
    @Lob
    private String treatmentGiven;
    private Double treatmentCost;
    private String treatmentOutcome;
    @Lob
    private String followupNotes;
    @Lob
    private String imagesJson;
    private String dataSource;
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
    private Boolean syntheticCase = false;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDiseaseId() { return diseaseId; }
    public void setDiseaseId(Long diseaseId) { this.diseaseId = diseaseId; }
    public String getCaseTitle() { return caseTitle; }
    public void setCaseTitle(String caseTitle) { this.caseTitle = caseTitle; }
    public LocalDateTime getVisitDate() { return visitDate; }
    public void setVisitDate(LocalDateTime visitDate) { this.visitDate = visitDate; }
    public String getHospital() { return hospital; }
    public void setHospital(String hospital) { this.hospital = hospital; }
    public Integer getPatientAge() { return patientAge; }
    public void setPatientAge(Integer patientAge) { this.patientAge = patientAge; }
    public String getPatientGender() { return patientGender; }
    public void setPatientGender(String patientGender) { this.patientGender = patientGender; }
    public String getAffectedArea() { return affectedArea; }
    public void setAffectedArea(String affectedArea) { this.affectedArea = affectedArea; }
    public String getSeverityLevel() { return severityLevel; }
    public void setSeverityLevel(String severityLevel) { this.severityLevel = severityLevel; }
    public String getChiefComplaint() { return chiefComplaint; }
    public void setChiefComplaint(String chiefComplaint) { this.chiefComplaint = chiefComplaint; }
    public String getPresentIllness() { return presentIllness; }
    public void setPresentIllness(String presentIllness) { this.presentIllness = presentIllness; }
    public String getPastHistory() { return pastHistory; }
    public void setPastHistory(String pastHistory) { this.pastHistory = pastHistory; }
    public String getPhysicalExamination() { return physicalExamination; }
    public void setPhysicalExamination(String physicalExamination) { this.physicalExamination = physicalExamination; }
    public String getLabResults() { return labResults; }
    public void setLabResults(String labResults) { this.labResults = labResults; }
    public String getImagingResults() { return imagingResults; }
    public void setImagingResults(String imagingResults) { this.imagingResults = imagingResults; }
    public String getSymptomDescription() { return symptomDescription; }
    public void setSymptomDescription(String symptomDescription) { this.symptomDescription = symptomDescription; }
    public String getDiagnosis() { return diagnosis; }
    public void setDiagnosis(String diagnosis) { this.diagnosis = diagnosis; }
    public String getTreatmentGiven() { return treatmentGiven; }
    public void setTreatmentGiven(String treatmentGiven) { this.treatmentGiven = treatmentGiven; }
    public Double getTreatmentCost() { return treatmentCost; }
    public void setTreatmentCost(Double treatmentCost) { this.treatmentCost = treatmentCost; }
    public String getTreatmentOutcome() { return treatmentOutcome; }
    public void setTreatmentOutcome(String treatmentOutcome) { this.treatmentOutcome = treatmentOutcome; }
    public String getFollowupNotes() { return followupNotes; }
    public void setFollowupNotes(String followupNotes) { this.followupNotes = followupNotes; }
    public String getImagesJson() { return imagesJson; }
    public void setImagesJson(String imagesJson) { this.imagesJson = imagesJson; }
    public String getDataSource() { return dataSource; }
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }
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
    public Boolean getSyntheticCase() { return syntheticCase; }
    public void setSyntheticCase(Boolean syntheticCase) { this.syntheticCase = syntheticCase; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
