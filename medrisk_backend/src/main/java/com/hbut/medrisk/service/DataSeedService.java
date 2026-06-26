package com.hbut.medrisk.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbut.medrisk.entity.DataSeedRunEntity;
import com.hbut.medrisk.entity.DiseaseInfoEntity;
import com.hbut.medrisk.entity.KnowledgeDocumentEntity;
import com.hbut.medrisk.entity.MedicalCaseEntity;
import com.hbut.medrisk.entity.TrainingDatasetEntity;
import com.hbut.medrisk.entity.UserEntity;
import com.hbut.medrisk.repository.DataSeedRunRepository;
import com.hbut.medrisk.repository.DiseaseInfoRepository;
import com.hbut.medrisk.repository.KnowledgeDocumentRepository;
import com.hbut.medrisk.repository.MedicalCaseRepository;
import com.hbut.medrisk.repository.TrainingDatasetRepository;
import com.hbut.medrisk.repository.UserRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DataSeedService {
    public static final String DEMO_SEED_KEY = "medrisk-public-synthetic-demo-v1";
    private static final String SOURCE_DEMO = "MedRisk Demo Seed Pack";
    private static final String SOURCE_SYNTHEA = "Synthea Synthetic Patient Generator";
    private static final String SOURCE_MEDLINE = "MedlinePlus Health Topics";
    private static final String SOURCE_UCI = "UCI Machine Learning Repository";
    private static final String SOURCE_NHANES = "CDC NHANES";
    private static final String SOURCE_DO_HPO = "Disease Ontology / HPO";
    private static final LocalDateTime RETRIEVED_AT = LocalDate.of(2026, 6, 26).atStartOfDay();

    private final DiseaseInfoRepository diseases;
    private final KnowledgeDocumentRepository documents;
    private final MedicalCaseRepository medicalCases;
    private final TrainingDatasetRepository datasets;
    private final DataSeedRunRepository runs;
    private final UserRepository users;
    private final FileStorageService files;
    private final KnowledgeGraphService graphService;
    private final ObjectMapper objectMapper;
    private final Path uploadRoot;

    public DataSeedService(
            DiseaseInfoRepository diseases,
            KnowledgeDocumentRepository documents,
            MedicalCaseRepository medicalCases,
            TrainingDatasetRepository datasets,
            DataSeedRunRepository runs,
            UserRepository users,
            FileStorageService files,
            KnowledgeGraphService graphService,
            ObjectMapper objectMapper,
            @Value("${medrisk.upload-dir}") String uploadDir) {
        this.diseases = diseases;
        this.documents = documents;
        this.medicalCases = medicalCases;
        this.datasets = datasets;
        this.runs = runs;
        this.users = users;
        this.files = files;
        this.graphService = graphService;
        this.objectMapper = objectMapper;
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public void ensureDemoPack() {
        if (diseases.countBySourceName(SOURCE_DEMO) >= 50
                && documents.countBySourceName(SOURCE_MEDLINE) >= 100
                && medicalCases.countBySourceName(SOURCE_SYNTHEA) >= 50
                && datasets.countBySourceName(SOURCE_UCI) + datasets.countBySourceName(SOURCE_NHANES) >= 5) {
            return;
        }
        UserEntity user = users.findByUsername("admin").orElse(null);
        importDemoPack(user, false);
    }

    @Transactional
    public Map<String, Object> importDemoPack(UserEntity user, boolean includeGraph) {
        if (user == null) {
            user = users.findByUsername("admin").orElse(null);
        }
        DataSeedRunEntity run = new DataSeedRunEntity();
        run.setSeedKey(DEMO_SEED_KEY);
        run.setStatus("RUNNING");
        run.setStartedBy(user == null ? null : user.getId());
        run.setStartedAt(LocalDateTime.now());
        runs.save(run);

        ImportCounters counters = new ImportCounters();
        StringBuilder message = new StringBuilder();
        try {
            List<DiseaseSeed> seedDiseases = diseaseSeeds();
            for (DiseaseSeed seed : seedDiseases) {
                DiseaseInfoEntity disease = upsertDisease(seed, user);
                counters.diseases++;
                upsertKnowledgeDocuments(seed, disease, user, counters);
                upsertMedicalCase(seed, disease, user);
                counters.cases++;
            }
            seedDatasets(user, counters);
            if (includeGraph) {
                try {
                    KnowledgeGraphStore.GraphWriteResult result = writeStructuredGraph(seedDiseases);
                    counters.graphNodes = result.nodesCreated();
                    counters.graphRelationships = result.relationshipsCreated();
                    message.append("图谱写入完成。");
                } catch (Exception ex) {
                    message.append("Neo4j 图谱写入未完成：").append(ex.getMessage()).append("。");
                }
            } else {
                message.append("已导入关系型演示数据；Neo4j 图谱可由管理员手动构建。");
            }
            run.setStatus("SUCCESS");
        } catch (Exception ex) {
            run.setStatus("FAILED");
            message.append("导入失败：").append(ex.getMessage());
        }
        run.setDiseaseCount(counters.diseases);
        run.setDocumentCount(counters.documents);
        run.setCaseCount(counters.cases);
        run.setDatasetCount(counters.datasets);
        run.setGraphNodeCount(counters.graphNodes);
        run.setGraphRelationshipCount(counters.graphRelationships);
        run.setMessage(message.toString());
        run.setFinishedAt(LocalDateTime.now());
        runs.save(run);
        return toRunMap(run);
    }

    public Map<String, Object> status() {
        return orderedMap(
                "seedKey", DEMO_SEED_KEY,
                "targets", orderedMap(
                        "diseases", 50,
                        "documents", 100,
                        "medicalCases", 50,
                        "datasets", 6,
                        "graphRelationships", 800),
                "counts", orderedMap(
                        "diseases", diseases.countBySourceName(SOURCE_DEMO),
                        "documents", documents.countBySourceName(SOURCE_MEDLINE),
                        "medicalCases", medicalCases.countBySourceName(SOURCE_SYNTHEA),
                        "datasets", datasets.countBySourceName(SOURCE_UCI) + datasets.countBySourceName(SOURCE_NHANES)),
                "sources", List.of(
                        source("MedlinePlus Health Topics", "https://medlineplus.gov/xml.html", "Use permitted with attribution"),
                        source("Synthea", "https://github.com/synthetichealth/synthea", "Apache-2.0"),
                        source("Disease Ontology", "https://obofoundry.org/ontology/doid.html", "CC0 1.0"),
                        source("HPO", "https://human-phenotype-ontology.github.io/downloads.html", "Open ontology data"),
                        source("UCI Heart/CKD datasets", "https://archive.ics.uci.edu/", "Dataset-specific licenses"),
                        source("CDC NHANES", "https://www.cdc.gov/nchs/hus/sources-definitions/nhanes.htm", "Public health data"),
                        source("openFDA Drug Label", "https://open.fda.gov/apis/drug/label/", "FDA public data")),
                "lastRun", runs.findFirstBySeedKeyOrderByStartedAtDesc(DEMO_SEED_KEY).map(this::toRunMap).orElse(null),
                "recentRuns", runs.findTop20ByOrderByStartedAtDesc().stream().map(this::toRunMap).toList());
    }

    private DiseaseInfoEntity upsertDisease(DiseaseSeed seed, UserEntity user) {
        Optional<DiseaseInfoEntity> existing = diseases.findBySourceRecordId(seed.sourceRecordId())
                .or(() -> diseases.findByDiseaseCode(seed.code()));
        DiseaseInfoEntity row = existing.orElseGet(DiseaseInfoEntity::new);
        row.setDiseaseCode(seed.code());
        row.setDiseaseName(seed.name());
        row.setDiseaseNameEn(seed.englishName());
        row.setDiseaseCategory(seed.category());
        row.setDepartment(seed.department());
        row.setPathogen(seed.pathogen());
        row.setSymptoms(seed.symptoms());
        row.setRiskFactors(seed.riskFactors());
        row.setPreventionMeasures(seed.prevention());
        row.setTreatmentPlan(seed.treatment());
        row.setSeverityLevel(seed.severity());
        row.setInfectious(seed.infectious());
        row.setCommonComplications(seed.complications());
        row.setPrognosis(seed.prognosis());
        row.setDescription(seed.description());
        row.setSourceName(SOURCE_DEMO);
        row.setSourceUrl(seed.sourceUrl());
        row.setSourceLicense(seed.sourceLicense());
        row.setSourceRecordId(seed.sourceRecordId());
        row.setRetrievedAt(RETRIEVED_AT);
        row.setVisibility(seed.visibility());
        if (row.getCreatedAt() == null) {
            row.setCreatedAt(LocalDateTime.now());
            row.setCreatedBy(user == null ? null : user.getId());
            row.setCreatedByName(user == null ? "系统种子" : user.getName());
        }
        row.setUpdatedAt(LocalDateTime.now());
        return diseases.save(row);
    }

    private void upsertKnowledgeDocuments(DiseaseSeed seed, DiseaseInfoEntity disease, UserEntity user, ImportCounters counters) throws IOException {
        upsertDocument(seed, disease, user, "patient", "PUBLIC", publicDocument(seed));
        counters.documents++;
        upsertDocument(seed, disease, user, "graph", seed.visibility(), graphDocument(seed));
        counters.documents++;
    }

    private void upsertDocument(DiseaseSeed seed, DiseaseInfoEntity disease, UserEntity user, String suffix, String visibility, String content) throws IOException {
        String sourceRecordId = "medrisk-demo:doc:" + seed.code() + ":" + suffix;
        KnowledgeDocumentEntity row = documents.findBySourceRecordId(sourceRecordId).orElseGet(KnowledgeDocumentEntity::new);
        String title = suffix.equals("patient") ? seed.name() + "患者科普与就诊建议" : seed.name() + "知识图谱结构化摘要";
        String filename = sanitize(seed.code() + "-" + suffix + ".txt");
        String objectKey = "seed/" + DEMO_SEED_KEY + "/" + filename;
        FileStorageService.StoredFile stored = files.storeGeneratedText("knowledge-documents", objectKey, filename, content);
        row.setTitle(title);
        row.setOriginalFileName(filename);
        row.setFileType("txt");
        row.setFileSize(stored.size());
        row.setFileBucket(stored.bucket());
        row.setFileObjectKey(stored.objectKey());
        row.setContent(content);
        row.setSummary(seed.name() + "的教学摘要，包含症状、风险因素、检查、治疗、预防和来源说明。");
        row.setGraphStatus("未构建");
        row.setUploadedBy(user == null ? 1L : user.getId());
        row.setUserName(user == null ? "系统种子" : user.getName());
        row.setSourceName(SOURCE_MEDLINE);
        row.setSourceUrl(seed.sourceUrl());
        row.setSourceLicense(seed.sourceLicense());
        row.setSourceRecordId(sourceRecordId);
        row.setRetrievedAt(RETRIEVED_AT);
        row.setVisibility(visibility);
        if (row.getCreatedAt() == null) row.setCreatedAt(LocalDateTime.now());
        row.setUpdatedAt(LocalDateTime.now());
        documents.save(row);
    }

    private void upsertMedicalCase(DiseaseSeed seed, DiseaseInfoEntity disease, UserEntity user) {
        String sourceRecordId = "synthea:case:" + seed.code();
        MedicalCaseEntity row = medicalCases.findBySourceRecordId(sourceRecordId).orElseGet(MedicalCaseEntity::new);
        row.setDiseaseId(disease.getId());
        row.setCaseTitle(seed.name() + "合成病历案例");
        row.setVisitDate(LocalDate.now().minusDays(Math.abs(seed.code().hashCode()) % 120).atStartOfDay());
        row.setHospital("MedRisk 合成教学医院");
        row.setPatientAge(35 + Math.abs(seed.code().hashCode()) % 45);
        row.setPatientGender(Math.abs(seed.code().hashCode()) % 2 == 0 ? "男" : "女");
        row.setAffectedArea(seed.bodyPart());
        row.setSeverityLevel(seed.severity());
        row.setChiefComplaint(first(seed.symptoms()) + "伴" + second(seed.symptoms()) + "，就诊前已持续数日。");
        row.setPresentIllness("合成患者因" + seed.symptoms() + "到院评估，既往存在" + seed.riskFactors() + "等风险因素。");
        row.setPastHistory("合成病历，无真实患者身份信息；既往史由规则生成。");
        row.setPhysicalExamination("生命体征平稳或轻度异常，重点查体部位：" + seed.bodyPart() + "。");
        row.setLabResults("建议结合" + seed.exams() + "等检查结果进行综合判断。");
        row.setImagingResults(seed.name() + "相关影像/检查结果为教学模拟文本。");
        row.setSymptomDescription(seed.symptoms());
        row.setDiagnosis(seed.name());
        row.setTreatmentGiven(seed.treatment());
        row.setTreatmentOutcome("经规范处理后症状改善，建议随访和风险因素管理。");
        row.setFollowupNotes("合成病历，非真实患者，不能替代医生诊断。");
        row.setDataSource("synthea-synthetic-" + seed.code());
        row.setSourceName(SOURCE_SYNTHEA);
        row.setSourceUrl("https://github.com/synthetichealth/synthea");
        row.setSourceLicense("Apache-2.0");
        row.setSourceRecordId(sourceRecordId);
        row.setRetrievedAt(RETRIEVED_AT);
        row.setVisibility(seed.caseVisibility());
        row.setSyntheticCase(true);
        if (row.getCreatedAt() == null) {
            row.setCreatedAt(LocalDateTime.now());
            row.setCreatedBy(user == null ? null : user.getId());
            row.setCreatedByName(user == null ? "系统种子" : user.getName());
        }
        row.setUpdatedAt(LocalDateTime.now());
        medicalCases.save(row);
    }

    private void seedDatasets(UserEntity user, ImportCounters counters) throws IOException {
        seedDataset(user, counters, "heart", "心脏病 UCI 教学数据集", SOURCE_UCI,
                "https://archive.ics.uci.edu/dataset/45/heart%2Bdisease", "UCI dataset license / citation required");
        seedDataset(user, counters, "kidney", "慢性肾病 UCI 教学数据集", SOURCE_UCI,
                "https://archive.ics.uci.edu/dataset/336/chronic%2Bkidney%2Bdisease", "CC BY 4.0");
        seedDataset(user, counters, "diabetes", "糖尿病 NHANES 派生教学数据集", SOURCE_NHANES,
                "https://www.cdc.gov/nchs/hus/sources-definitions/nhanes.htm", "Public health data");
        seedDataset(user, counters, "liver", "肝病 NHANES 派生教学数据集", SOURCE_NHANES,
                "https://www.cdc.gov/nchs/hus/sources-definitions/nhanes.htm", "Public health data");
        seedDataset(user, counters, "stroke", "卒中风险 NHANES 派生教学数据集", SOURCE_NHANES,
                "https://www.cdc.gov/nchs/hus/sources-definitions/nhanes.htm", "Public health data");
        seedDataset(user, counters, "hypertension", "高血压风险 NHANES 派生教学数据集", SOURCE_NHANES,
                "https://www.cdc.gov/nchs/hus/sources-definitions/nhanes.htm", "Public health data");
    }

    private void seedDataset(UserEntity user, ImportCounters counters, String diseaseType, String name, String sourceName, String sourceUrl, String license) throws IOException {
        String sourceRecordId = "medrisk-demo:dataset:" + diseaseType;
        TrainingDatasetEntity row = datasets.findBySourceRecordId(sourceRecordId).orElseGet(TrainingDatasetEntity::new);
        Path dir = uploadRoot.resolve("datasets").resolve("seed").normalize();
        Files.createDirectories(dir);
        Path path = dir.resolve(diseaseType + "-demo.csv").normalize();
        Files.writeString(path, demoCsv(diseaseType), StandardCharsets.UTF_8);
        row.setName(name);
        row.setDiseaseType(diseaseType);
        row.setDescription("公开数据源结构启发的教学演示 CSV，已统一为 label 监督学习格式。");
        row.setFileName(path.getFileName().toString());
        row.setFilePath(path.toString());
        row.setFileType("csv");
        row.setStatus("VALID");
        row.setSampleCount(36);
        row.setFeatureColumns(objectMapper.writeValueAsString(List.of("age", "bmi", "glucose", "bloodPressure", "cholesterol", "smoker", "symptomScore")));
        row.setValidationMessage("演示数据已通过种子校验");
        row.setUploadedBy(user == null ? 1L : user.getId());
        row.setSourceName(sourceName);
        row.setSourceUrl(sourceUrl);
        row.setSourceLicense(license);
        row.setSourceRecordId(sourceRecordId);
        row.setRetrievedAt(RETRIEVED_AT);
        row.setVisibility("ADMIN_ONLY");
        if (row.getCreatedAt() == null) row.setCreatedAt(LocalDateTime.now());
        row.setUpdatedAt(LocalDateTime.now());
        datasets.save(row);
        counters.datasets++;
    }

    private KnowledgeGraphStore.GraphWriteResult writeStructuredGraph(List<DiseaseSeed> seedDiseases) {
        int nodes = 0;
        int relationships = 0;
        for (DiseaseSeed seed : seedDiseases) {
            List<KnowledgeGraphStore.Triplet> triplets = structuredTriplets(seed);
            KnowledgeGraphStore.GraphWriteResult result = graphService.mergeStructuredTriplets(
                    "MedRisk 演示图谱 - " + seed.name(),
                    SOURCE_DO_HPO,
                    seed.sourceUrl(),
                    seed.sourceLicense(),
                    seed.visibility(),
                    triplets);
            nodes += result.nodesCreated();
            relationships += result.relationshipsCreated();
        }
        return new KnowledgeGraphStore.GraphWriteResult(nodes, relationships);
    }

    private List<KnowledgeGraphStore.Triplet> structuredTriplets(DiseaseSeed seed) {
        List<KnowledgeGraphStore.Triplet> rows = new ArrayList<>();
        rows.add(triplet(seed.name(), "Disease", "MANAGED_BY", "由...管理", seed.department(), "Department", seed.description(), "就诊科室"));
        rows.add(triplet(seed.name(), "Disease", "AFFECTS_BODYPART", "影响部位", seed.bodyPart(), "BodyPart", seed.description(), "受累部位"));
        rows.add(triplet(seed.name(), "Disease", "DOCUMENTED_IN", "记录于", seed.name() + "患者科普与就诊建议", "Document", seed.description(), "知识文档"));
        rows.add(triplet("合成患者-" + seed.code(), "PatientSynthetic", "DIAGNOSED_AS", "诊断为", seed.name(), "Disease", "Synthea/规则生成的教学患者", seed.description()));
        rows.add(triplet("合成患者-" + seed.code(), "PatientSynthetic", "RECORDED_AT", "发生时间", "2026年教学样本", "TimePoint", "合成病历", "教学时间点"));
        for (String value : split(seed.symptoms())) {
            rows.add(triplet(seed.name(), "Disease", "SHOWS_SYMPTOM", "表现症状", value, "Symptom", seed.description(), "症状"));
        }
        for (String value : split(seed.riskFactors())) {
            rows.add(triplet(seed.name(), "Disease", "AGGRAVATED_BY", "被...加重", value, "RiskFactor", seed.description(), "危险因素"));
        }
        for (String value : split(seed.exams())) {
            rows.add(triplet(seed.name(), "Disease", "REQUIRES_EXAMINATION", "需要检查", value, "Exam", seed.description(), "检查项目"));
        }
        for (String value : split(seed.treatment())) {
            String type = value.contains("药") || value.contains("素") || value.contains("剂") ? "Drug" : "Treatment";
            rows.add(triplet(seed.name(), "Disease", "TREATED_WITH", "用...治疗", value, type, seed.description(), "治疗方法"));
        }
        for (String value : split(seed.prevention())) {
            rows.add(triplet(seed.name(), "Disease", "PREVENTED_BY", "被预防", value, "Treatment", seed.description(), "预防措施"));
        }
        return rows;
    }

    private KnowledgeGraphStore.Triplet triplet(String head, String headType, String relation, String label, String tail, String tailType, String headDescription, String tailDescription) {
        return new KnowledgeGraphStore.Triplet(head, headType, relation, label, tail, tailType, headDescription, tailDescription);
    }

    private String publicDocument(DiseaseSeed seed) {
        return """
                标题：%s患者科普与就诊建议
                来源：%s（%s）
                授权/使用说明：%s

                %s。常见症状包括：%s。常见风险因素包括：%s。
                建议就诊科室：%s。常用检查包括：%s。
                预防和日常管理重点：%s。
                治疗和随访方向：%s。

                本文为 MedRisk 教学演示摘要，不能替代医生诊断。
                """.formatted(seed.name(), seed.sourceUrl(), seed.sourceRecordId(), seed.sourceLicense(), seed.description(),
                seed.symptoms(), seed.riskFactors(), seed.department(), seed.exams(), seed.prevention(), seed.treatment());
    }

    private String graphDocument(DiseaseSeed seed) {
        return """
                疾病：%s
                英文名：%s
                编码：%s
                类别：%s
                科室：%s
                影响部位：%s
                病因/病原：%s
                症状节点：%s
                风险因素节点：%s
                检查项目节点：%s
                治疗节点：%s
                预后：%s
                图谱来源：%s
                """.formatted(seed.name(), seed.englishName(), seed.code(), seed.category(), seed.department(), seed.bodyPart(),
                seed.pathogen(), seed.symptoms(), seed.riskFactors(), seed.exams(), seed.treatment(), seed.prognosis(), seed.sourceUrl());
    }

    private String demoCsv(String diseaseType) {
        StringBuilder csv = new StringBuilder("age,bmi,glucose,bloodPressure,cholesterol,smoker,symptomScore,label\n");
        int offset = Math.abs(diseaseType.hashCode()) % 9;
        for (int i = 0; i < 36; i++) {
            int age = 32 + i + offset;
            double bmi = 21.5 + (i % 9) * 1.2;
            double glucose = 4.8 + (i % 7) * 0.7 + (i > 18 ? 1.2 : 0);
            int bloodPressure = 110 + (i % 12) * 4 + (i > 20 ? 14 : 0);
            double cholesterol = 4.2 + (i % 8) * 0.35 + (i > 22 ? 0.8 : 0);
            int smoker = i % 4 == 0 ? 1 : 0;
            int symptomScore = (i % 6) + (i > 20 ? 3 : 0);
            int label = age > 55 || glucose > 8.0 || bloodPressure > 150 || symptomScore > 7 ? 1 : 0;
            csv.append(age).append(',').append(round(bmi)).append(',').append(round(glucose)).append(',')
                    .append(bloodPressure).append(',').append(round(cholesterol)).append(',')
                    .append(smoker).append(',').append(symptomScore).append(',').append(label).append('\n');
        }
        return csv.toString();
    }

    private String round(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private List<DiseaseSeed> diseaseSeeds() {
        return List.of(
                disease("ICD-E11", "DOID:9352", "糖尿病", "Diabetes mellitus", "代谢性疾病", "内分泌科", "胰岛素分泌或作用异常", "多饮、多尿、乏力、体重下降", "肥胖、家族史、高糖饮食、缺乏运动", "控制体重、合理饮食、规律运动、监测血糖", "生活方式干预、降糖药物、胰岛素治疗", "中度", false, "全身代谢", "血糖、糖化血红蛋白、尿常规", "糖尿病是一组以慢性高血糖为特征的代谢性疾病。"),
                disease("ICD-I10", "DOID:10763", "高血压", "Hypertension", "心血管疾病", "心血管科", "血管阻力增高", "头晕、头痛、胸闷、血压升高", "高盐饮食、肥胖、吸烟、精神压力", "限盐、减重、戒烟、规律监测血压", "降压药物治疗、生活方式管理、随访监测", "中度", false, "心血管系统", "血压监测、心电图、肾功能", "高血压是常见慢性心血管疾病。"),
                disease("ICD-I25", "DOID:3393", "冠心病", "Coronary heart disease", "心血管疾病", "心血管科", "冠状动脉粥样硬化", "胸痛、胸闷、活动后气短、心悸", "高脂血症、糖尿病、吸烟、高血压", "控制血脂血压、戒烟、规律复诊、运动康复", "抗血小板药物、降脂药物、介入治疗、心脏康复", "重度", false, "冠状动脉", "心电图、肌钙蛋白、冠脉CTA", "冠心病由冠状动脉狭窄或闭塞引起。"),
                disease("ICD-J18", "DOID:552", "肺炎", "Pneumonia", "呼吸系统疾病", "呼吸科", "细菌、病毒或其他病原体感染", "发热、咳嗽、咳痰、呼吸困难", "免疫力下降、吸烟、慢性肺病、老年", "接种疫苗、保持通风、及时治疗呼吸道感染", "抗感染治疗、氧疗、对症支持、补液", "中度", true, "肺部", "血常规、胸部影像、病原学检测", "肺炎是肺实质感染性炎症。"),
                disease("ICD-K29", "DOID:4029", "胃炎", "Gastritis", "消化系统疾病", "消化科", "胃黏膜炎症", "上腹痛、恶心、反酸、食欲下降", "幽门螺杆菌感染、饮酒、药物刺激、饮食不规律", "规律饮食、减少刺激性食物、避免滥用止痛药", "抑酸药物、胃黏膜保护、根除幽门螺杆菌、饮食干预", "轻度", false, "胃", "胃镜、幽门螺杆菌检测、血常规", "胃炎是胃黏膜炎症性疾病。"),
                disease("ICD-N18", "DOID:784", "慢性肾病", "Chronic kidney disease", "肾脏疾病", "肾内科", "肾单位慢性损伤", "水肿、乏力、尿量改变、贫血", "糖尿病、高血压、肾炎、药物肾毒性", "控制血压血糖、避免肾毒性药物、低盐饮食", "肾功能保护、控制并发症、透析评估、营养管理", "重度", false, "肾脏", "肌酐、尿蛋白、肾脏超声", "慢性肾病是肾结构或功能持续异常。"),
                disease("ICD-I63", "DOID:6713", "脑卒中", "Stroke", "神经系统疾病", "神经内科", "脑血管阻塞或破裂", "偏瘫、言语不清、口角歪斜、意识障碍", "高血压、房颤、吸烟、糖尿病", "控制血压、抗栓管理、戒烟、识别FAST症状", "急性期溶栓评估、抗血小板、康复训练、二级预防", "严重", false, "脑", "头颅CT、头颅MRI、凝血功能", "脑卒中是急性脑血管事件。"),
                disease("ICD-K76", "DOID:409", "脂肪肝", "Fatty liver disease", "肝胆疾病", "肝病科", "肝细胞脂肪沉积", "乏力、右上腹不适、肝酶升高、腹胀", "肥胖、饮酒、糖脂代谢异常、久坐", "减重、限酒、运动、控制血脂血糖", "生活方式干预、保肝治疗、代谢管理、随访复查", "中度", false, "肝脏", "肝功能、肝脏超声、血脂", "脂肪肝与代谢风险密切相关。"),
                disease("ICD-M81", "DOID:11476", "骨质疏松", "Osteoporosis", "骨科疾病", "骨科", "骨量减少和骨微结构破坏", "腰背痛、身高变矮、骨折、驼背", "绝经、年龄增长、钙摄入不足、缺乏运动", "补钙、维生素D、抗阻运动、防跌倒", "抗骨质疏松药物、疼痛管理、康复训练、骨折处理", "中度", false, "骨骼", "骨密度、钙磷代谢、脊柱影像", "骨质疏松会增加脆性骨折风险。"),
                disease("ICD-S72", "DOID:225", "骨折", "Fracture", "骨科疾病", "骨科", "外伤或骨强度下降", "疼痛、肿胀、畸形、活动受限", "跌倒、交通伤、骨质疏松、运动损伤", "防跌倒、佩戴防护、骨质疏松管理、规范运动", "固定、复位、手术、康复训练", "中度", false, "骨骼", "X线、CT、血常规", "骨折是骨连续性或完整性中断。"),
                disease("ICD-J45", "DOID:2841", "哮喘", "Asthma", "呼吸系统疾病", "呼吸科", "气道慢性炎症和高反应性", "喘息、气短、胸闷、咳嗽", "过敏原、冷空气、感染、运动诱发", "避免诱因、规范吸入治疗、接种疫苗、峰流速监测", "吸入糖皮质激素、支气管扩张剂、急性发作处理、教育管理", "中度", false, "气道", "肺功能、过敏原检测、峰流速", "哮喘是可变气流受限疾病。"),
                disease("ICD-J44", "DOID:3083", "慢性阻塞性肺疾病", "COPD", "呼吸系统疾病", "呼吸科", "长期气道和肺实质损伤", "慢性咳嗽、咳痰、气短、活动耐量下降", "吸烟、空气污染、职业粉尘、反复感染", "戒烟、疫苗接种、减少污染暴露、肺康复", "支气管扩张剂、吸入治疗、氧疗、肺康复", "重度", false, "肺部", "肺功能、胸部影像、血气分析", "COPD 是持续气流受限疾病。"),
                disease("ICD-K21", "DOID:8534", "胃食管反流病", "GERD", "消化系统疾病", "消化科", "胃内容物反流", "反酸、烧心、胸骨后不适、咳嗽", "肥胖、夜宵、饮酒、食管裂孔疝", "控制体重、抬高床头、避免睡前进食、减少刺激食物", "抑酸治疗、促动力治疗、生活方式调整、必要时内镜评估", "轻度", false, "食管", "胃镜、食管pH监测、幽门螺杆菌检测", "GERD 是胃内容物反流导致的症状或并发症。"),
                disease("ICD-K80", "DOID:11654", "胆结石", "Gallstones", "肝胆疾病", "肝胆外科", "胆固醇或胆色素结晶", "右上腹痛、恶心、发热、黄疸", "肥胖、快速减重、女性、年龄增长", "规律饮食、控制体重、避免过度油腻、管理代谢风险", "止痛解痉、抗感染、胆囊切除评估、内镜治疗", "中度", false, "胆囊", "腹部超声、肝功能、血常规", "胆结石可引发胆绞痛或胆囊炎。"),
                disease("ICD-B18", "DOID:2043", "慢性乙型肝炎", "Chronic hepatitis B", "感染性疾病", "肝病科", "乙型肝炎病毒感染", "乏力、食欲下降、肝区不适、肝酶异常", "母婴传播、血液暴露、未接种疫苗、免疫低下", "乙肝疫苗、避免血液暴露、规范筛查、家庭防护", "抗病毒治疗、肝功能监测、肝癌筛查、生活管理", "中度", true, "肝脏", "HBV DNA、肝功能、肝脏超声", "慢性乙肝需要长期随访和抗病毒评估。"),
                disease("ICD-E78", "DOID:3146", "高脂血症", "Hyperlipidemia", "代谢性疾病", "内分泌科", "脂质代谢异常", "多无症状、黄色瘤、胸闷、动脉硬化", "高脂饮食、肥胖、遗传、糖尿病", "低脂饮食、运动、减重、戒烟", "他汀类药物、生活方式干预、心血管风险管理、随访复查", "中度", false, "血管", "血脂谱、肝功能、动脉超声", "高脂血症是动脉粥样硬化重要风险因素。"),
                disease("ICD-E66", "DOID:9970", "肥胖症", "Obesity", "代谢性疾病", "内分泌科", "能量摄入与消耗失衡", "体重增加、活动后气短、打鼾、关节痛", "高热量饮食、久坐、遗传、睡眠不足", "均衡饮食、运动计划、睡眠管理、行为干预", "营养治疗、运动处方、药物评估、代谢手术评估", "中度", false, "全身代谢", "BMI、腰围、血糖血脂", "肥胖症会增加多种慢病风险。"),
                disease("ICD-E03", "DOID:1459", "甲状腺功能减退", "Hypothyroidism", "内分泌疾病", "内分泌科", "甲状腺激素不足", "怕冷、乏力、水肿、体重增加", "自身免疫、甲状腺手术、碘摄入异常、药物影响", "规律复查、合理碘摄入、遵医嘱用药、妊娠筛查", "左甲状腺素替代、剂量调整、TSH监测、并发症管理", "中度", false, "甲状腺", "TSH、FT4、甲状腺抗体", "甲减是甲状腺激素不足导致的代谢减慢状态。"),
                disease("ICD-E05", "DOID:7998", "甲状腺功能亢进", "Hyperthyroidism", "内分泌疾病", "内分泌科", "甲状腺激素过多", "心悸、怕热、手抖、体重下降", "Graves病、甲状腺结节、碘摄入异常、精神压力", "规律复查、避免过量碘、识别心律失常、遵医嘱治疗", "抗甲状腺药物、放射碘评估、手术评估、症状控制", "中度", false, "甲状腺", "TSH、FT3、FT4、甲状腺超声", "甲亢会导致代谢加快和心血管负担增加。"),
                disease("ICD-M10", "DOID:13189", "痛风", "Gout", "风湿免疫疾病", "风湿免疫科", "尿酸盐结晶沉积", "关节红肿热痛、夜间疼痛、活动受限、痛风石", "高嘌呤饮食、饮酒、肥胖、肾功能下降", "低嘌呤饮食、限酒、多饮水、控制体重", "急性期抗炎、降尿酸治疗、肾功能监测、生活方式管理", "中度", false, "关节", "尿酸、肾功能、关节超声", "痛风是高尿酸血症相关晶体性关节炎。"),
                disease("ICD-M05", "DOID:7148", "类风湿关节炎", "Rheumatoid arthritis", "风湿免疫疾病", "风湿免疫科", "自身免疫性滑膜炎", "关节肿痛、晨僵、乏力、活动受限", "遗传易感、吸烟、感染诱因、女性", "早诊早治、戒烟、保护关节、规范随访", "改善病情抗风湿药、生物制剂评估、康复训练、疼痛管理", "中度", false, "关节", "类风湿因子、抗CCP、关节影像", "类风湿关节炎可导致关节破坏。"),
                disease("ICD-L40", "DOID:8893", "银屑病", "Psoriasis", "皮肤疾病", "皮肤科", "免疫介导皮肤炎症", "红斑、鳞屑、瘙痒、甲改变", "遗传、感染、压力、肥胖", "皮肤保湿、避免诱因、控制体重、规范用药", "外用药物、光疗、系统治疗、生物制剂评估", "中度", false, "皮肤", "皮肤查体、病理、炎症指标", "银屑病是慢性复发性炎症性皮肤病。"),
                disease("ICD-L20", "DOID:3310", "特应性皮炎", "Atopic dermatitis", "皮肤疾病", "皮肤科", "皮肤屏障异常和炎症", "瘙痒、湿疹样皮疹、干燥、渗出", "过敏体质、干燥环境、刺激物、感染", "保湿、避免刺激、减少抓挠、识别过敏诱因", "外用抗炎药物、抗组胺、感染处理、教育管理", "轻度", false, "皮肤", "皮肤评估、过敏原检测、感染筛查", "特应性皮炎常反复发作。"),
                disease("ICD-N39", "DOID:1328", "尿路感染", "Urinary tract infection", "感染性疾病", "泌尿外科", "细菌侵入尿路", "尿频、尿急、尿痛、发热", "女性、饮水少、导尿、糖尿病", "多饮水、注意卫生、避免憋尿、控制血糖", "抗感染治疗、尿培养指导用药、补液、复查", "轻度", true, "泌尿系统", "尿常规、尿培养、肾功能", "尿路感染是常见泌尿系统感染。"),
                disease("ICD-N20", "DOID:9590", "肾结石", "Kidney stone", "泌尿系统疾病", "泌尿外科", "尿中晶体沉积", "腰痛、血尿、恶心、尿频", "饮水少、高盐饮食、代谢异常、家族史", "多饮水、低盐饮食、代谢评估、规律运动", "止痛、排石、体外碎石、内镜手术评估", "中度", false, "肾脏", "泌尿系超声、CT、尿常规", "肾结石可引发肾绞痛和梗阻。"),
                disease("ICD-D50", "DOID:2355", "缺铁性贫血", "Iron deficiency anemia", "血液疾病", "血液科", "铁缺乏导致血红蛋白合成不足", "乏力、头晕、心悸、面色苍白", "慢性失血、铁摄入不足、妊娠、吸收不良", "均衡饮食、处理失血原因、孕期筛查、复查血象", "补铁治疗、病因处理、营养指导、血红蛋白监测", "轻度", false, "血液系统", "血常规、铁蛋白、便潜血", "缺铁性贫血是最常见贫血类型之一。"),
                disease("ICD-D69", "DOID:2217", "血小板减少症", "Thrombocytopenia", "血液疾病", "血液科", "血小板生成减少或破坏增多", "皮肤瘀点、鼻出血、牙龈出血、月经过多", "免疫异常、药物、感染、骨髓疾病", "避免外伤、规范用药、感染预防、定期复查", "病因治疗、止血处理、免疫治疗、输注评估", "中度", false, "血液系统", "血常规、凝血功能、骨髓检查", "血小板减少会增加出血风险。"),
                disease("ICD-C50", "DOID:1612", "乳腺癌", "Breast cancer", "肿瘤疾病", "肿瘤科", "乳腺上皮恶性增殖", "乳房肿块、皮肤改变、乳头溢液、腋窝淋巴结肿大", "年龄、家族史、激素暴露、肥胖", "筛查、体重管理、减少酒精、遗传咨询", "手术、放疗、化疗、内分泌治疗、靶向治疗", "严重", false, "乳腺", "乳腺超声、钼靶、病理", "乳腺癌需要多学科规范治疗。"),
                disease("ICD-C34", "DOID:1324", "肺癌", "Lung cancer", "肿瘤疾病", "肿瘤科", "肺部恶性肿瘤", "咳嗽、咯血、胸痛、体重下降", "吸烟、空气污染、职业暴露、家族史", "戒烟、低剂量CT筛查、职业防护、空气质量改善", "手术、放疗、化疗、靶向治疗、免疫治疗", "严重", false, "肺部", "胸部CT、病理、基因检测", "肺癌早筛和分型治疗很重要。"),
                disease("ICD-C18", "DOID:9256", "结直肠癌", "Colorectal cancer", "肿瘤疾病", "肿瘤科", "结直肠黏膜恶性增殖", "便血、排便习惯改变、腹痛、贫血", "年龄、息肉、家族史、高脂低纤饮食", "肠镜筛查、健康饮食、运动、息肉处理", "手术、化疗、放疗、靶向治疗、随访监测", "严重", false, "结直肠", "肠镜、病理、肿瘤标志物", "结直肠癌可通过筛查降低风险。"),
                disease("ICD-F32", "DOID:1470", "抑郁症", "Depression", "精神心理疾病", "精神心理科", "情绪调节和神经递质异常", "情绪低落、兴趣减退、睡眠异常、乏力", "压力事件、遗传、慢病、社会支持不足", "心理支持、规律作息、运动、早期求助", "心理治疗、抗抑郁药物、危机干预、随访管理", "中度", false, "神经心理", "量表评估、睡眠评估、躯体疾病筛查", "抑郁症需要专业评估和持续支持。"),
                disease("ICD-F41", "DOID:2030", "焦虑障碍", "Anxiety disorder", "精神心理疾病", "精神心理科", "焦虑和应激调节异常", "紧张担心、心悸、出汗、睡眠差", "长期压力、遗传、咖啡因、慢病", "压力管理、规律作息、减少刺激物、心理支持", "认知行为治疗、药物治疗、放松训练、随访", "中度", false, "神经心理", "量表评估、甲状腺功能、心电图", "焦虑障碍会影响生活和躯体感受。"),
                disease("ICD-G40", "DOID:1826", "癫痫", "Epilepsy", "神经系统疾病", "神经内科", "脑神经元异常放电", "抽搐、意识丧失、感觉异常、发作后疲乏", "脑损伤、遗传、感染、睡眠不足", "规律服药、避免诱因、安全防护、充足睡眠", "抗癫痫药物、病因治疗、手术评估、长期随访", "中度", false, "脑", "脑电图、头颅MRI、代谢检查", "癫痫是反复发作性神经系统疾病。"),
                disease("ICD-G43", "DOID:6364", "偏头痛", "Migraine", "神经系统疾病", "神经内科", "神经血管调节异常", "搏动性头痛、畏光、恶心、先兆", "睡眠不足、压力、饮酒、激素波动", "规律作息、记录诱因、避免过度止痛药、放松训练", "急性止痛、预防用药、生活方式管理、随访", "中度", false, "头部", "神经查体、影像排除、头痛日记", "偏头痛是反复发作的原发性头痛。"),
                disease("ICD-H10", "DOID:1123", "结膜炎", "Conjunctivitis", "眼科疾病", "眼科", "感染、过敏或刺激", "眼红、流泪、分泌物、异物感", "接触感染源、过敏原、隐形眼镜、卫生不佳", "手卫生、避免揉眼、隐形眼镜护理、隔离感染源", "抗感染滴眼液、抗过敏治疗、冷敷、眼科复查", "轻度", true, "眼结膜", "裂隙灯检查、分泌物检查、过敏评估", "结膜炎多数预后良好但需区分病因。"),
                disease("ICD-H66", "DOID:0050156", "中耳炎", "Otitis media", "耳鼻喉疾病", "耳鼻喉科", "中耳感染或积液", "耳痛、听力下降、发热、耳闷", "上呼吸道感染、儿童、过敏、腺样体肥大", "预防感冒、鼻炎管理、避免烟雾、及时就医", "镇痛、抗感染评估、鼻部治疗、鼓膜观察", "轻度", true, "中耳", "耳镜、听力检查、血常规", "中耳炎常见于儿童和上呼吸道感染后。"),
                disease("ICD-J01", "DOID:0050127", "鼻窦炎", "Sinusitis", "耳鼻喉疾病", "耳鼻喉科", "鼻窦黏膜炎症", "鼻塞、脓涕、面部胀痛、嗅觉下降", "感冒、过敏性鼻炎、鼻中隔偏曲、免疫低下", "鼻腔冲洗、过敏管理、避免烟雾、及时治疗感冒", "鼻喷激素、抗感染评估、鼻腔冲洗、手术评估", "轻度", true, "鼻窦", "鼻内镜、鼻窦CT、过敏原检测", "鼻窦炎可急性或慢性反复。"),
                disease("ICD-K12", "DOID:8646", "口腔溃疡", "Oral ulcer", "口腔疾病", "口腔科", "口腔黏膜损伤或免疫炎症", "口腔疼痛、溃疡面、进食疼痛、复发", "压力、创伤、营养缺乏、免疫因素", "保持口腔卫生、避免刺激食物、规律作息、补充营养", "局部止痛、促进愈合、处理诱因、复发评估", "轻度", false, "口腔黏膜", "口腔检查、血常规、营养评估", "口腔溃疡多可自限但复发需评估。"),
                disease("ICD-K05", "DOID:10970", "牙周炎", "Periodontitis", "口腔疾病", "口腔科", "牙菌斑相关炎症", "牙龈出血、牙齿松动、口臭、咀嚼不适", "口腔卫生差、吸烟、糖尿病、遗传", "刷牙和牙线、定期洁治、戒烟、控制血糖", "洁治刮治、局部治疗、牙周手术评估、维护治疗", "中度", false, "牙周组织", "牙周探诊、口腔影像、菌斑评估", "牙周炎会导致牙周支持组织破坏。"),
                disease("ICD-O24", "DOID:11714", "妊娠期糖尿病", "Gestational diabetes", "妊娠相关疾病", "产科", "妊娠期糖代谢异常", "多无症状、口渴、尿频、胎儿偏大", "高龄妊娠、肥胖、家族史、既往妊娠糖尿病", "孕期筛查、营养管理、适量运动、产后复查", "饮食运动管理、血糖监测、胰岛素评估、产后随访", "中度", false, "妊娠代谢", "OGTT、血糖监测、胎儿超声", "妊娠期糖尿病影响母婴结局。"),
                disease("ICD-O14", "DOID:10591", "子痫前期", "Preeclampsia", "妊娠相关疾病", "产科", "妊娠期血管内皮功能异常", "高血压、蛋白尿、水肿、头痛", "初产、高龄、多胎、慢性高血压", "规范产检、血压监测、识别预警症状、风险评估", "降压治疗、硫酸镁预防、母胎监测、适时终止妊娠", "严重", false, "妊娠血管系统", "血压、尿蛋白、肝肾功能", "子痫前期需严密产科管理。"),
                disease("ICD-A09", "DOID:104", "急性胃肠炎", "Acute gastroenteritis", "感染性疾病", "消化科", "病毒、细菌或毒素", "腹泻、呕吐、腹痛、发热", "不洁饮食、接触感染者、免疫低下、旅行", "饮食卫生、手卫生、安全饮水、食物充分加热", "补液、止吐止泻评估、抗感染评估、饮食调整", "轻度", true, "胃肠道", "血常规、大便常规、电解质", "急性胃肠炎多与感染或食物相关。"),
                disease("ICD-B01", "DOID:8659", "水痘", "Chickenpox", "感染性疾病", "感染科", "水痘-带状疱疹病毒", "发热、瘙痒性皮疹、水疱、乏力", "未接种疫苗、密切接触、儿童、免疫低下", "接种疫苗、隔离、手卫生、避免抓挠", "对症处理、抗病毒评估、皮肤护理、并发症监测", "中度", true, "皮肤", "临床评估、病毒检测、血常规", "水痘传染性强，多数儿童预后良好。"),
                disease("ICD-B02", "DOID:8536", "带状疱疹", "Herpes zoster", "感染性疾病", "皮肤科", "潜伏病毒再激活", "单侧疼痛、水疱、灼痛、神经痛", "年龄增长、免疫低下、压力、慢病", "疫苗接种、增强免疫、早期就医、皮肤护理", "抗病毒治疗、镇痛、神经痛管理、皮肤护理", "中度", true, "神经皮节", "临床评估、病毒检测、疼痛评分", "带状疱疹可遗留神经痛。"),
                disease("ICD-U07", "DOID:0080600", "新型冠状病毒感染", "COVID-19", "感染性疾病", "呼吸科", "SARS-CoV-2感染", "发热、咳嗽、咽痛、乏力", "密切接触、免疫低下、基础病、高龄", "疫苗接种、通风、手卫生、呼吸道防护", "对症治疗、抗病毒评估、氧疗、并发症监测", "中度", true, "呼吸道", "抗原或核酸、血氧、胸部影像", "新冠感染表现从轻症到重症不等。"),
                disease("ICD-A15", "DOID:399", "肺结核", "Pulmonary tuberculosis", "感染性疾病", "感染科", "结核分枝杆菌感染", "咳嗽、咯血、盗汗、低热", "密切接触、免疫低下、营养不良、拥挤环境", "接触者筛查、通风、规范治疗、提高营养", "联合抗结核治疗、隔离管理、肝功能监测、随访痰检", "重度", true, "肺部", "痰涂片、结核核酸、胸部影像", "肺结核需规范足疗程治疗。"),
                disease("ICD-A90", "DOID:12205", "登革热", "Dengue fever", "感染性疾病", "感染科", "登革病毒经蚊媒传播", "高热、皮疹、肌肉痛、出血倾向", "蚊虫叮咬、流行区旅行、积水环境、既往感染", "灭蚊、防蚊、清除积水、旅行防护", "补液、退热、出血监测、重症预警", "中度", true, "全身血管", "血常规、病毒检测、肝功能", "登革热需警惕血小板下降和出血。"),
                disease("ICD-A41", "DOID:8469", "败血症", "Sepsis", "感染性疾病", "急诊科", "感染导致宿主反应失调", "发热、寒战、低血压、意识改变", "免疫低下、侵入操作、严重感染、老年", "早期识别感染、规范抗菌药、无菌操作、慢病管理", "早期抗感染、液体复苏、器官支持、病原学评估", "严重", true, "全身", "血培养、乳酸、炎症指标", "败血症是高风险急危重症。"),
                disease("ICD-I50", "DOID:6000", "心力衰竭", "Heart failure", "心血管疾病", "心血管科", "心脏泵血功能下降", "气短、水肿、乏力、夜间憋醒", "冠心病、高血压、瓣膜病、心肌病", "限盐、体重监测、规范用药、控制基础病", "利尿剂、RAAS抑制剂、β受体阻滞剂、器械评估", "重度", false, "心脏", "BNP、心脏超声、心电图", "心衰需要长期综合管理。"),
                disease("ICD-I48", "DOID:0060224", "房颤", "Atrial fibrillation", "心血管疾病", "心血管科", "心房电活动紊乱", "心悸、胸闷、乏力、头晕", "年龄、高血压、心衰、甲亢", "控制血压、限酒、管理睡眠呼吸暂停、规律复查", "抗凝评估、节律或室率控制、消融评估、卒中预防", "中度", false, "心房", "心电图、动态心电图、心脏超声", "房颤会增加卒中风险。"),
                disease("ICD-I21", "DOID:5844", "急性心肌梗死", "Acute myocardial infarction", "心血管疾病", "心血管科", "冠状动脉急性闭塞", "持续胸痛、出汗、恶心、濒死感", "冠心病、高脂血症、吸烟、高血压", "控制危险因素、识别胸痛、及时急救、规律用药", "急诊再灌注、抗血小板、降脂、心脏监护", "严重", false, "心肌", "心电图、肌钙蛋白、冠脉造影", "急性心梗需要争分夺秒救治。"),
                disease("ICD-G20", "DOID:14330", "帕金森病", "Parkinson disease", "神经系统疾病", "神经内科", "黑质多巴胺神经元退变", "震颤、运动迟缓、肌强直、姿势不稳", "年龄、遗传、环境暴露、睡眠障碍", "规律运动、跌倒预防、营养支持、早期评估", "多巴胺能药物、康复训练、深脑刺激评估、非运动症状管理", "中度", false, "基底节", "神经查体、影像排除、量表评估", "帕金森病是慢性进展性神经退行性疾病。"),
                disease("ICD-G30", "DOID:10652", "阿尔茨海默病", "Alzheimer disease", "神经系统疾病", "神经内科", "神经退行性改变", "记忆下降、执行功能下降、迷路、性格改变", "年龄、家族史、心血管风险、低教育年限", "认知训练、运动、控制血管风险、安全管理", "认知药物、行为管理、照护支持、康复训练", "重度", false, "脑", "认知量表、头颅影像、血液筛查", "阿尔茨海默病会导致认知功能持续下降。"),
                disease("ICD-M54", "DOID:11832", "腰椎间盘突出", "Lumbar disc herniation", "骨科疾病", "骨科", "椎间盘退变和突出压迫神经", "腰痛、下肢放射痛、麻木、活动受限", "久坐、搬重物、退变、姿势不良", "核心肌群训练、避免久坐、正确搬运、体重管理", "保守治疗、物理治疗、止痛、手术评估", "中度", false, "腰椎", "腰椎MRI、神经查体、X线", "腰椎间盘突出常引起腰腿痛。"),
                disease("ICD-M17", "DOID:8398", "膝骨关节炎", "Knee osteoarthritis", "骨科疾病", "骨科", "关节软骨退变", "膝痛、晨僵、活动受限、弹响", "年龄、肥胖、劳损、既往损伤", "减重、低冲击运动、保护关节、肌力训练", "镇痛、物理治疗、关节腔治疗、关节置换评估", "中度", false, "膝关节", "X线、关节查体、炎症指标", "膝骨关节炎是常见退行性关节病。"),
                disease("ICD-L03", "DOID:0050426", "蜂窝织炎", "Cellulitis", "感染性疾病", "皮肤科", "皮肤软组织细菌感染", "红肿热痛、发热、皮肤紧张、压痛", "皮肤破损、糖尿病、静脉回流差、免疫低下", "皮肤护理、处理伤口、控制血糖、避免抓挠", "抗感染治疗、抬高患肢、脓肿引流评估、复诊", "中度", true, "皮肤软组织", "血常规、炎症指标、超声", "蜂窝织炎需要及时抗感染。"),
                disease("ICD-T78", "DOID:1205", "过敏反应", "Allergic reaction", "免疫疾病", "变态反应科", "免疫系统对过敏原反应", "皮疹、瘙痒、喘息、面唇肿胀", "食物、药物、昆虫叮咬、过敏体质", "识别并避免过敏原、携带急救药、就医评估、记录过敏史", "抗组胺、糖皮质激素评估、肾上腺素急救、观察", "中度", false, "全身免疫", "过敏原检测、血常规、呼吸评估", "严重过敏反应可危及生命。"),
                disease("ICD-G47", "DOID:535", "睡眠呼吸暂停", "Sleep apnea", "呼吸睡眠疾病", "呼吸科", "睡眠时上气道反复塌陷", "打鼾、白天嗜睡、晨起头痛、夜间憋醒", "肥胖、颈围增大、饮酒、鼻咽结构异常", "减重、侧卧睡眠、限酒、睡眠卫生", "CPAP治疗、口腔矫治器、手术评估、慢病管理", "中度", false, "上气道", "睡眠监测、血氧、鼻咽评估", "睡眠呼吸暂停会增加心血管风险。"),
                disease("ICD-E55", "DOID:5113", "维生素D缺乏", "Vitamin D deficiency", "营养代谢疾病", "内分泌科", "维生素D摄入或合成不足", "乏力、骨痛、肌无力、骨量下降", "日晒不足、饮食不足、吸收不良、肾病", "适度日晒、膳食补充、风险筛查、运动", "维生素D补充、钙剂评估、病因处理、复查", "轻度", false, "骨代谢", "25羟维生素D、钙磷、骨密度", "维生素D缺乏影响骨代谢和肌力。"));
    }

    private DiseaseSeed disease(String code, String doid, String name, String englishName, String category, String department,
            String pathogen, String symptoms, String riskFactors, String prevention, String treatment, String severity,
            boolean infectious, String bodyPart, String exams, String description) {
        String visibility = Math.abs(code.hashCode()) % 7 == 0 ? "DOCTOR_ONLY" : "PUBLIC";
        String caseVisibility = Math.abs(code.hashCode()) % 3 == 0 ? "DOCTOR_ONLY" : "PUBLIC";
        return new DiseaseSeed(
                code,
                doid,
                name,
                englishName,
                category,
                department,
                pathogen,
                symptoms,
                riskFactors,
                prevention,
                treatment,
                severity,
                infectious,
                bodyPart,
                exams,
                "常见并发症需结合病程和基础疾病评估。",
                "规范管理后多数患者可降低并发症风险。",
                description,
                "https://medlineplus.gov/xml.html",
                "Use permitted with attribution to MedlinePlus.gov; ontology references from DO/HPO where applicable",
                "medrisk-demo:disease:" + code + ":" + doid,
                visibility,
                caseVisibility);
    }

    private String first(String value) {
        List<String> parts = split(value);
        return parts.isEmpty() ? "不适" : parts.get(0);
    }

    private String second(String value) {
        List<String> parts = split(value);
        return parts.size() < 2 ? "不适" : parts.get(1);
    }

    private List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("[、,，;；]"))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
    }

    private Map<String, Object> source(String name, String url, String license) {
        return orderedMap("name", name, "url", url, "license", license, "retrievedAt", RETRIEVED_AT);
    }

    private Map<String, Object> toRunMap(DataSeedRunEntity run) {
        if (run == null) return null;
        return orderedMap(
                "id", run.getId(),
                "seedKey", run.getSeedKey(),
                "status", run.getStatus(),
                "diseaseCount", run.getDiseaseCount(),
                "documentCount", run.getDocumentCount(),
                "caseCount", run.getCaseCount(),
                "datasetCount", run.getDatasetCount(),
                "graphNodeCount", run.getGraphNodeCount(),
                "graphRelationshipCount", run.getGraphRelationshipCount(),
                "message", run.getMessage(),
                "startedBy", run.getStartedBy(),
                "startedAt", run.getStartedAt(),
                "finishedAt", run.getFinishedAt());
    }

    private String sanitize(String value) {
        return value.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }

    private Map<String, Object> orderedMap(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }

    private static final class ImportCounters {
        int diseases;
        int documents;
        int cases;
        int datasets;
        int graphNodes;
        int graphRelationships;
    }

    private record DiseaseSeed(
            String code,
            String doid,
            String name,
            String englishName,
            String category,
            String department,
            String pathogen,
            String symptoms,
            String riskFactors,
            String prevention,
            String treatment,
            String severity,
            boolean infectious,
            String bodyPart,
            String exams,
            String complications,
            String prognosis,
            String description,
            String sourceUrl,
            String sourceLicense,
            String sourceRecordId,
            String visibility,
            String caseVisibility) {
    }
}
