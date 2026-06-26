package com.hbut.medrisk.service;

import com.hbut.medrisk.entity.DiseaseInfoEntity;
import com.hbut.medrisk.entity.UserEntity;
import com.hbut.medrisk.repository.DiseaseInfoRepository;
import jakarta.persistence.EntityNotFoundException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DiseaseInfoService {
    private final DiseaseInfoRepository diseases;
    private final FileStorageService files;
    private final AuditService auditService;

    public DiseaseInfoService(DiseaseInfoRepository diseases, FileStorageService files, AuditService auditService) {
        this.diseases = diseases;
        this.files = files;
        this.auditService = auditService;
    }

    public List<Map<String, Object>> list(String keyword, String department, UserEntity user) {
        String kw = clean(keyword).toLowerCase(Locale.ROOT);
        String dep = clean(department).toLowerCase(Locale.ROOT);
        return diseases.newest().stream()
                .filter(row -> VisibilityPolicy.canRead(row.getVisibility(), row.getCreatedBy(), user))
                .filter(row -> kw.isBlank()
                        || contains(row.getDiseaseName(), kw)
                        || contains(row.getDiseaseCode(), kw)
                        || contains(row.getSymptoms(), kw)
                        || contains(row.getDescription(), kw))
                .filter(row -> dep.isBlank() || contains(row.getDepartment(), dep))
                .limit(200)
                .map(this::toMap)
                .toList();
    }

    public List<Map<String, Object>> search(String keyword, UserEntity user) {
        String kw = clean(keyword);
        if (kw.isBlank()) {
            return List.of();
        }
        return diseases.findTop20ByDiseaseNameContainingIgnoreCaseOrDiseaseCodeContainingIgnoreCaseOrderByCreatedAtDesc(kw, kw)
                .stream()
                .filter(row -> VisibilityPolicy.canRead(row.getVisibility(), row.getCreatedBy(), user))
                .map(this::toMap).toList();
    }

    public Map<String, Object> get(Long id, UserEntity user) {
        DiseaseInfoEntity row = requireDisease(id);
        if (!VisibilityPolicy.canRead(row.getVisibility(), row.getCreatedBy(), user)) {
            throw new SecurityException("不能访问该疾病信息");
        }
        return toMap(row);
    }

    @Transactional
    public Map<String, Object> create(Map<String, String> fields, MultipartFile image, UserEntity user) throws IOException {
        String code = required(fields, "diseaseCode", "疾病编号不能为空");
        if (diseases.existsByDiseaseCode(code)) {
            throw new IllegalArgumentException("疾病编号已存在");
        }
        DiseaseInfoEntity disease = new DiseaseInfoEntity();
        apply(disease, fields);
        disease.setVisibility(VisibilityPolicy.normalize(fields.get("visibility"), "ADMIN".equals(user.getRole()) ? "PUBLIC" : "DRAFT"));
        disease.setCreatedBy(user.getId());
        disease.setCreatedByName(user.getName());
        LocalDateTime now = LocalDateTime.now();
        disease.setCreatedAt(now);
        disease.setUpdatedAt(now);
        storeImage(disease, image);
        diseases.save(disease);
        auditService.log(user.getId(), "CREATE_DISEASE", "DISEASE", disease.getId().toString(), "{}");
        return toMap(disease);
    }

    @Transactional
    public Map<String, Object> update(Long id, Map<String, String> fields, MultipartFile image, UserEntity user) throws IOException {
        DiseaseInfoEntity disease = requireDisease(id);
        String code = fields.get("diseaseCode");
        if (!blank(code) && diseases.existsByDiseaseCodeAndIdNot(code.trim(), id)) {
            throw new IllegalArgumentException("疾病编号已存在");
        }
        apply(disease, fields);
        if (fields.containsKey("visibility")) {
            disease.setVisibility(VisibilityPolicy.normalize(fields.get("visibility"), disease.getVisibility()));
        }
        storeImage(disease, image);
        disease.setUpdatedAt(LocalDateTime.now());
        auditService.log(user.getId(), "UPDATE_DISEASE", "DISEASE", disease.getId().toString(), "{}");
        return toMap(disease);
    }

    @Transactional
    public Map<String, Object> delete(Long id, UserEntity user) {
        DiseaseInfoEntity disease = requireDisease(id);
        diseases.delete(disease);
        auditService.log(user.getId(), "DELETE_DISEASE", "DISEASE", id.toString(), "{}");
        return Map.of("deleted", true, "id", id);
    }

    public DiseaseInfoEntity requireDisease(Long id) {
        return diseases.findById(id).orElseThrow(() -> new EntityNotFoundException("疾病信息不存在"));
    }

    Map<String, Object> toMap(DiseaseInfoEntity row) {
        return orderedMap(
                "id", row.getId(),
                "diseaseCode", row.getDiseaseCode(),
                "diseaseName", row.getDiseaseName(),
                "diseaseNameEn", row.getDiseaseNameEn(),
                "diseaseCategory", row.getDiseaseCategory(),
                "department", row.getDepartment(),
                "pathogen", row.getPathogen(),
                "symptoms", row.getSymptoms(),
                "riskFactors", row.getRiskFactors(),
                "preventionMeasures", row.getPreventionMeasures(),
                "treatmentPlan", row.getTreatmentPlan(),
                "severityLevel", row.getSeverityLevel(),
                "infectious", Boolean.TRUE.equals(row.getInfectious()),
                "incubationPeriod", row.getIncubationPeriod(),
                "commonComplications", row.getCommonComplications(),
                "prognosis", row.getPrognosis(),
                "description", row.getDescription(),
                "imageUrl", imageUrl(row.getImageBucket(), row.getImageObjectKey()),
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

    private void apply(DiseaseInfoEntity disease, Map<String, String> fields) {
        if (fields.containsKey("diseaseCode")) disease.setDiseaseCode(required(fields, "diseaseCode", "疾病编号不能为空"));
        if (fields.containsKey("diseaseName")) disease.setDiseaseName(required(fields, "diseaseName", "疾病名称不能为空"));
        if (fields.containsKey("diseaseNameEn")) disease.setDiseaseNameEn(clean(fields.get("diseaseNameEn")));
        if (fields.containsKey("diseaseCategory")) disease.setDiseaseCategory(clean(fields.get("diseaseCategory")));
        if (fields.containsKey("department")) disease.setDepartment(clean(fields.get("department")));
        if (fields.containsKey("pathogen")) disease.setPathogen(clean(fields.get("pathogen")));
        if (fields.containsKey("symptoms")) disease.setSymptoms(clean(fields.get("symptoms")));
        if (fields.containsKey("riskFactors")) disease.setRiskFactors(clean(fields.get("riskFactors")));
        if (fields.containsKey("preventionMeasures")) disease.setPreventionMeasures(clean(fields.get("preventionMeasures")));
        if (fields.containsKey("treatmentPlan")) disease.setTreatmentPlan(clean(fields.get("treatmentPlan")));
        if (fields.containsKey("severityLevel")) disease.setSeverityLevel(clean(fields.get("severityLevel")));
        if (fields.containsKey("infectious")) disease.setInfectious(Boolean.parseBoolean(clean(fields.get("infectious"))));
        if (fields.containsKey("incubationPeriod")) disease.setIncubationPeriod(clean(fields.get("incubationPeriod")));
        if (fields.containsKey("commonComplications")) disease.setCommonComplications(clean(fields.get("commonComplications")));
        if (fields.containsKey("prognosis")) disease.setPrognosis(clean(fields.get("prognosis")));
        if (fields.containsKey("description")) disease.setDescription(clean(fields.get("description")));
        if (fields.containsKey("sourceName")) disease.setSourceName(clean(fields.get("sourceName")));
        if (fields.containsKey("sourceUrl")) disease.setSourceUrl(clean(fields.get("sourceUrl")));
        if (fields.containsKey("sourceLicense")) disease.setSourceLicense(clean(fields.get("sourceLicense")));
        if (fields.containsKey("sourceRecordId")) disease.setSourceRecordId(clean(fields.get("sourceRecordId")));
        if (disease.getInfectious() == null) disease.setInfectious(false);
        if (disease.getVisibility() == null) disease.setVisibility("PUBLIC");
    }

    private void storeImage(DiseaseInfoEntity disease, MultipartFile image) throws IOException {
        if (image != null && !image.isEmpty()) {
            FileStorageService.StoredFile stored = files.store("disease-images", image);
            disease.setImageBucket(stored.bucket());
            disease.setImageObjectKey(stored.objectKey());
        }
    }

    private String imageUrl(String bucket, String key) {
        return blank(bucket) || blank(key) ? null : "/api/files/" + bucket + "/" + key;
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

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private Map<String, Object> orderedMap(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        return map;
    }
}
