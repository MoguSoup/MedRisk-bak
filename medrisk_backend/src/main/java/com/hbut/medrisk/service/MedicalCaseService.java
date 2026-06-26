package com.hbut.medrisk.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbut.medrisk.entity.DiseaseInfoEntity;
import com.hbut.medrisk.entity.MedicalCaseEntity;
import com.hbut.medrisk.entity.UserEntity;
import com.hbut.medrisk.repository.MedicalCaseRepository;
import jakarta.persistence.EntityNotFoundException;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MedicalCaseService {
    private final MedicalCaseRepository cases;
    private final DiseaseInfoService diseaseService;
    private final FileStorageService files;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public MedicalCaseService(
            MedicalCaseRepository cases,
            DiseaseInfoService diseaseService,
            FileStorageService files,
            AuditService auditService,
            ObjectMapper objectMapper) {
        this.cases = cases;
        this.diseaseService = diseaseService;
        this.files = files;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> list(String keyword, Long diseaseId, String hospital, UserEntity user) {
        String kw = clean(keyword).toLowerCase(Locale.ROOT);
        String hp = clean(hospital).toLowerCase(Locale.ROOT);
        return cases.newest().stream()
                .filter(row -> VisibilityPolicy.canRead(row.getVisibility(), row.getCreatedBy(), user))
                .filter(row -> diseaseId == null || row.getDiseaseId().equals(diseaseId))
                .filter(row -> hp.isBlank() || contains(row.getHospital(), hp))
                .filter(row -> kw.isBlank()
                        || contains(row.getCaseTitle(), kw)
                        || contains(row.getHospital(), kw)
                        || contains(row.getDiagnosis(), kw)
                        || contains(row.getSymptomDescription(), kw))
                .limit(200)
                .map(row -> toMap(row, user))
                .toList();
    }

    public List<Map<String, Object>> search(String keyword, UserEntity user) {
        String kw = clean(keyword).toLowerCase(Locale.ROOT);
        if (kw.isBlank()) {
            return List.of();
        }
        return cases.newest().stream()
                .filter(row -> VisibilityPolicy.canRead(row.getVisibility(), row.getCreatedBy(), user))
                .filter(row -> contains(row.getCaseTitle(), kw) || contains(row.getHospital(), kw)
                        || contains(row.getDiagnosis(), kw) || contains(row.getSymptomDescription(), kw))
                .limit(10)
                .map(row -> toMap(row, user))
                .toList();
    }

    public Map<String, Object> get(Long id, UserEntity user) {
        MedicalCaseEntity row = requireCase(id);
        if (!VisibilityPolicy.canRead(row.getVisibility(), row.getCreatedBy(), user)) {
            throw new SecurityException("不能访问该病历");
        }
        return toMap(row, user);
    }

    @Transactional
    public Map<String, Object> create(Map<String, String> fields, List<MultipartFile> images, UserEntity user) throws IOException {
        MedicalCaseEntity row = new MedicalCaseEntity();
        apply(row, fields);
        row.setVisibility(VisibilityPolicy.normalize(fields.get("visibility"), "ADMIN".equals(user.getRole()) ? "PUBLIC" : "DRAFT"));
        row.setCreatedBy(user.getId());
        row.setCreatedByName(user.getName());
        appendImages(row, images);
        LocalDateTime now = LocalDateTime.now();
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        cases.save(row);
        auditService.log(user.getId(), "CREATE_MEDICAL_CASE", "MEDICAL_CASE", row.getId().toString(), "{}");
        return toMap(row);
    }

    @Transactional
    public Map<String, Object> update(Long id, Map<String, String> fields, List<MultipartFile> images, UserEntity user) throws IOException {
        MedicalCaseEntity row = requireCase(id);
        apply(row, fields);
        if (fields.containsKey("visibility")) {
            row.setVisibility(VisibilityPolicy.normalize(fields.get("visibility"), row.getVisibility()));
        }
        appendImages(row, images);
        row.setUpdatedAt(LocalDateTime.now());
        auditService.log(user.getId(), "UPDATE_MEDICAL_CASE", "MEDICAL_CASE", row.getId().toString(), "{}");
        return toMap(row);
    }

    @Transactional
    public Map<String, Object> delete(Long id, UserEntity user) {
        MedicalCaseEntity row = requireCase(id);
        cases.delete(row);
        auditService.log(user.getId(), "DELETE_MEDICAL_CASE", "MEDICAL_CASE", id.toString(), "{}");
        return Map.of("deleted", true, "id", id);
    }

    public MedicalCaseEntity requireCase(Long id) {
        return cases.findById(id).orElseThrow(() -> new EntityNotFoundException("病历不存在"));
    }

    Map<String, Object> toMap(MedicalCaseEntity row) {
        return toMap(row, null);
    }

    Map<String, Object> toMap(MedicalCaseEntity row, UserEntity user) {
        DiseaseInfoEntity disease = diseaseService.requireDisease(row.getDiseaseId());
        if (user != null && "PATIENT".equals(user.getRole())) {
            return orderedMap(
                    "id", row.getId(),
                    "diseaseId", row.getDiseaseId(),
                    "diseaseName", disease.getDiseaseName(),
                    "caseTitle", row.getCaseTitle(),
                    "severityLevel", row.getSeverityLevel(),
                    "chiefComplaint", row.getChiefComplaint(),
                    "symptomDescription", row.getSymptomDescription(),
                    "diagnosis", row.getDiagnosis(),
                    "treatmentGiven", row.getTreatmentGiven(),
                    "treatmentOutcome", row.getTreatmentOutcome(),
                    "followupNotes", row.getFollowupNotes(),
                    "syntheticCase", Boolean.TRUE.equals(row.getSyntheticCase()),
                    "dataSource", row.getDataSource(),
                    "visibility", row.getVisibility(),
                    "visibilityLabel", VisibilityPolicy.display(row.getVisibility()),
                    "sourceName", row.getSourceName(),
                    "sourceUrl", row.getSourceUrl(),
                    "sourceLicense", row.getSourceLicense(),
                    "createdAt", row.getCreatedAt(),
                    "updatedAt", row.getUpdatedAt());
        }
        return orderedMap(
                "id", row.getId(),
                "diseaseId", row.getDiseaseId(),
                "diseaseName", disease.getDiseaseName(),
                "caseTitle", row.getCaseTitle(),
                "visitDate", row.getVisitDate(),
                "hospital", row.getHospital(),
                "patientAge", row.getPatientAge(),
                "patientGender", row.getPatientGender(),
                "affectedArea", row.getAffectedArea(),
                "severityLevel", row.getSeverityLevel(),
                "chiefComplaint", row.getChiefComplaint(),
                "presentIllness", row.getPresentIllness(),
                "pastHistory", row.getPastHistory(),
                "physicalExamination", row.getPhysicalExamination(),
                "labResults", row.getLabResults(),
                "imagingResults", row.getImagingResults(),
                "symptomDescription", row.getSymptomDescription(),
                "diagnosis", row.getDiagnosis(),
                "treatmentGiven", row.getTreatmentGiven(),
                "treatmentCost", row.getTreatmentCost(),
                "treatmentOutcome", row.getTreatmentOutcome(),
                "followupNotes", row.getFollowupNotes(),
                "images", readImages(row.getImagesJson()),
                "dataSource", row.getDataSource(),
                "syntheticCase", Boolean.TRUE.equals(row.getSyntheticCase()),
                "visibility", row.getVisibility(),
                "visibilityLabel", VisibilityPolicy.display(row.getVisibility()),
                "sourceName", row.getSourceName(),
                "sourceUrl", row.getSourceUrl(),
                "sourceLicense", row.getSourceLicense(),
                "sourceRecordId", row.getSourceRecordId(),
                "retrievedAt", row.getRetrievedAt(),
                "createdBy", row.getCreatedBy(),
                "createdByName", row.getCreatedByName(),
                "createdAt", row.getCreatedAt(),
                "updatedAt", row.getUpdatedAt());
    }

    private void apply(MedicalCaseEntity row, Map<String, String> fields) {
        if (fields.containsKey("diseaseId")) {
            Long diseaseId = Long.parseLong(required(fields, "diseaseId", "请选择关联疾病"));
            diseaseService.requireDisease(diseaseId);
            row.setDiseaseId(diseaseId);
        }
        if (fields.containsKey("caseTitle")) row.setCaseTitle(required(fields, "caseTitle", "病历标题不能为空"));
        if (fields.containsKey("visitDate")) row.setVisitDate(parseDate(fields.get("visitDate")));
        if (fields.containsKey("hospital")) row.setHospital(clean(fields.get("hospital")));
        if (fields.containsKey("patientAge")) row.setPatientAge(parseInt(fields.get("patientAge")));
        if (fields.containsKey("patientGender")) row.setPatientGender(clean(fields.get("patientGender")));
        if (fields.containsKey("affectedArea")) row.setAffectedArea(clean(fields.get("affectedArea")));
        if (fields.containsKey("severityLevel")) row.setSeverityLevel(clean(fields.get("severityLevel")));
        if (fields.containsKey("chiefComplaint")) row.setChiefComplaint(clean(fields.get("chiefComplaint")));
        if (fields.containsKey("presentIllness")) row.setPresentIllness(clean(fields.get("presentIllness")));
        if (fields.containsKey("pastHistory")) row.setPastHistory(clean(fields.get("pastHistory")));
        if (fields.containsKey("physicalExamination")) row.setPhysicalExamination(clean(fields.get("physicalExamination")));
        if (fields.containsKey("labResults")) row.setLabResults(clean(fields.get("labResults")));
        if (fields.containsKey("imagingResults")) row.setImagingResults(clean(fields.get("imagingResults")));
        if (fields.containsKey("symptomDescription")) row.setSymptomDescription(clean(fields.get("symptomDescription")));
        if (fields.containsKey("diagnosis")) row.setDiagnosis(clean(fields.get("diagnosis")));
        if (fields.containsKey("treatmentGiven")) row.setTreatmentGiven(clean(fields.get("treatmentGiven")));
        if (fields.containsKey("treatmentCost")) row.setTreatmentCost(parseDouble(fields.get("treatmentCost")));
        if (fields.containsKey("treatmentOutcome")) row.setTreatmentOutcome(clean(fields.get("treatmentOutcome")));
        if (fields.containsKey("followupNotes")) row.setFollowupNotes(clean(fields.get("followupNotes")));
        if (fields.containsKey("dataSource")) row.setDataSource(clean(fields.get("dataSource")));
        if (fields.containsKey("sourceName")) row.setSourceName(clean(fields.get("sourceName")));
        if (fields.containsKey("sourceUrl")) row.setSourceUrl(clean(fields.get("sourceUrl")));
        if (fields.containsKey("sourceLicense")) row.setSourceLicense(clean(fields.get("sourceLicense")));
        if (fields.containsKey("sourceRecordId")) row.setSourceRecordId(clean(fields.get("sourceRecordId")));
        if (fields.containsKey("syntheticCase")) row.setSyntheticCase(Boolean.parseBoolean(clean(fields.get("syntheticCase"))));
        if (row.getVisibility() == null) row.setVisibility("PUBLIC");
        if (row.getSyntheticCase() == null) row.setSyntheticCase(false);
    }

    private void appendImages(MedicalCaseEntity row, List<MultipartFile> images) throws IOException {
        if (images == null || images.isEmpty()) {
            return;
        }
        List<Map<String, Object>> current = new ArrayList<>(readImages(row.getImagesJson()));
        for (MultipartFile image : images) {
            if (image == null || image.isEmpty()) continue;
            FileStorageService.StoredFile stored = files.store("medical-case-images", image);
            current.add(orderedMap(
                    "bucket", stored.bucket(),
                    "objectKey", stored.objectKey(),
                    "url", stored.url(),
                    "filename", stored.originalFilename()));
        }
        row.setImagesJson(objectMapper.writeValueAsString(current));
    }

    private List<Map<String, Object>> readImages(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private LocalDateTime parseDate(String value) {
        String cleaned = clean(value);
        if (cleaned.isBlank()) return null;
        if (cleaned.length() == 10) return LocalDate.parse(cleaned).atStartOfDay();
        return LocalDateTime.parse(cleaned);
    }

    private Integer parseInt(String value) {
        String cleaned = clean(value);
        return cleaned.isBlank() ? null : Integer.parseInt(cleaned);
    }

    private Double parseDouble(String value) {
        String cleaned = clean(value);
        return cleaned.isBlank() ? null : Double.parseDouble(cleaned);
    }

    private String required(Map<String, String> fields, String key, String message) {
        String value = clean(fields.get(key));
        if (value.isBlank()) throw new IllegalArgumentException(message);
        return value;
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private Map<String, Object> orderedMap(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        return map;
    }
}
