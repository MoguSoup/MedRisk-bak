package com.hbut.medrisk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbut.medrisk.entity.DiseaseInfoEntity;
import com.hbut.medrisk.entity.MedicalCaseEntity;
import com.hbut.medrisk.entity.ModelVersionEntity;
import com.hbut.medrisk.entity.UserEntity;
import com.hbut.medrisk.repository.DiseaseInfoRepository;
import com.hbut.medrisk.repository.MedicalCaseRepository;
import com.hbut.medrisk.repository.ModelVersionRepository;
import com.hbut.medrisk.repository.UserRepository;
import com.hbut.medrisk.service.DataSeedService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
public class MedRiskApplication {
    public static void main(String[] args) {
        SpringApplication.run(MedRiskApplication.class, args);
    }

    @Bean
    CommandLineRunner seedDemoData(
            UserRepository users,
            ModelVersionRepository models,
            DiseaseInfoRepository diseases,
            MedicalCaseRepository medicalCases,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper,
            DataSeedService dataSeeds) {
        return args -> {
            seedUser(users, passwordEncoder, "admin", "admin@medrisk.local", "管理员", "ADMIN");
            seedUser(users, passwordEncoder, "doctor", "doctor@medrisk.local", "演示医生", "DOCTOR");
            seedUser(users, passwordEncoder, "patient", "patient@medrisk.local", "演示患者", "PATIENT");

            if (models.count() == 0) {
                List<Object[]> defaults = List.of(
                        new Object[] {"diabetes", "糖尿病", "XGBoost 公开数据部署基线", "diabetes-xgb-teaching-v1.0.0", 0.88, 0.84, 0.86, 0.85, 0.91, "CDC BRFSS 2024 Diabetes", "CDC BRFSS 2024", "https://www.cdc.gov/brfss/annual_data/annual_2024.html", 453241},
                        new Object[] {"heart", "心脏病", "XGBoost 公开数据部署基线", "heart-xgb-teaching-v1.0.0", 0.90, 0.86, 0.88, 0.87, 0.93, "CDC BRFSS 2024 Heart Disease", "CDC BRFSS 2024", "https://www.cdc.gov/brfss/annual_data/annual_2024.html", 452464},
                        new Object[] {"kidney", "慢性肾病", "XGBoost 公开数据部署基线", "kidney-lightgbm-teaching-v1.0.0", 0.87, 0.83, 0.89, 0.86, 0.92, "CDC BRFSS 2024 Chronic Kidney Disease", "CDC BRFSS 2024", "https://www.cdc.gov/brfss/annual_data/annual_2024.html", 455691},
                        new Object[] {"liver", "肝病", "XGBoost 公开数据部署基线", "liver-catboost-teaching-v1.0.0", 0.86, 0.82, 0.85, 0.83, 0.90, "CDC NHANES 2017-March 2020 Liver", "CDC NHANES 2017-March 2020", "https://wwwn.cdc.gov/nchs/nhanes/continuousnhanes/default.aspx?Cycle=2017-2020", 9213},
                        new Object[] {"stroke", "中风", "XGBoost 公开数据部署基线", "stroke-rf-teaching-v1.0.0", 0.89, 0.84, 0.90, 0.87, 0.94, "CDC BRFSS 2024 Stroke", "CDC BRFSS 2024", "https://www.cdc.gov/brfss/annual_data/annual_2024.html", 456218});
                for (Object[] row : defaults) {
                    ModelVersionEntity model = new ModelVersionEntity();
                    model.setDiseaseType((String) row[0]);
                    model.setDiseaseName((String) row[1]);
                    model.setModelName((String) row[2]);
                    model.setVersion((String) row[3]);
                    model.setMetricsJson(objectMapper.writeValueAsString(Map.ofEntries(
                            Map.entry("accuracy", row[4]),
                            Map.entry("precision", row[5]),
                            Map.entry("recall", row[6]),
                            Map.entry("f1", row[7]),
                            Map.entry("auc", row[8]),
                            Map.entry("evaluationDataset", row[9]),
                            Map.entry("datasetSource", row[10]),
                            Map.entry("datasetUrl", row[11]),
                            Map.entry("sampleCount", row[12]),
                            Map.entry("validationType", "held-out public dataset evaluation"))));
                    model.setFeatureSchemaJson("[]");
                    model.setHyperparametersJson(objectMapper.writeValueAsString(Map.of(
                            "nEstimators", 80,
                            "maxDepth", 3,
                            "learningRate", 0.05,
                            "subsample", 0.9,
                            "colsampleBytree", 0.9,
                            "regLambda", 1.0,
                            "minChildWeight", 1.0)));
                    model.setEvaluationDatasetName((String) row[9]);
                    model.setEvaluationDatasetSource((String) row[10]);
                    model.setEvaluationDatasetUrl((String) row[11]);
                    model.setModelPath("models/" + row[0] + "/" + row[3]);
                    model.setActive(true);
                    model.setCreatedAt(LocalDateTime.now());
                    models.save(model);
                }
            }
            seedKnowledgeDemo(diseases, medicalCases);
            dataSeeds.ensureDemoPack();
        };
    }

    private void seedUser(UserRepository users, PasswordEncoder encoder, String username, String email, String name, String role) {
        users.findByUsername(username).map(user -> {
            boolean changed = false;
            if (user.getStatus() == null) {
                user.setStatus("ACTIVE");
                changed = true;
            }
            if (user.getUpdatedAt() == null) {
                user.setUpdatedAt(user.getCreatedAt() == null ? LocalDateTime.now() : user.getCreatedAt());
                changed = true;
            }
            return changed ? users.save(user) : user;
        }).orElseGet(() -> {
            UserEntity user = new UserEntity();
            user.setUsername(username);
            user.setEmail(email);
            user.setName(name);
            user.setRole(role);
            user.setPasswordHash(encoder.encode("123456"));
            user.setStatus("ACTIVE");
            LocalDateTime now = LocalDateTime.now();
            user.setCreatedAt(now);
            user.setUpdatedAt(now);
            return users.save(user);
        });
    }

    private void seedKnowledgeDemo(DiseaseInfoRepository diseases, MedicalCaseRepository medicalCases) {
        seedDisease(diseases, medicalCases, "ICD-E11", "糖尿病", "Diabetes mellitus", "代谢性疾病", "内分泌科",
                "胰岛素分泌或作用异常", "多饮、多尿、乏力、体重下降", "肥胖、家族史、高糖饮食、缺乏运动",
                "控制体重、合理饮食、规律运动、监测血糖", "生活方式干预、降糖药物、胰岛素治疗", "中度", false,
                "糖尿病是一组以慢性高血糖为特征的代谢性疾病，需要长期管理。",
                "糖尿病病历样例", "空腹血糖升高伴多饮多尿", "2型糖尿病", "饮食控制联合二甲双胍治疗");
        seedDisease(diseases, medicalCases, "ICD-I10", "高血压", "Hypertension", "心血管疾病", "心血管科",
                "血管阻力增高", "头晕、头痛、胸闷、血压升高", "高盐饮食、肥胖、吸烟、精神压力",
                "限盐、减重、戒烟、规律监测血压", "降压药物治疗和生活方式管理", "中度", false,
                "高血压是常见慢性心血管疾病，长期控制可降低卒中和冠心病风险。",
                "高血压病历样例", "反复头晕伴血压升高", "原发性高血压", "钙通道阻滞剂联合生活方式干预");
        seedDisease(diseases, medicalCases, "ICD-I25", "冠心病", "Coronary heart disease", "心血管疾病", "心血管科",
                "冠状动脉粥样硬化", "胸痛、胸闷、活动后气短", "高脂血症、糖尿病、吸烟、高血压",
                "控制血脂血压、戒烟、规律复诊", "抗血小板、降脂、必要时介入治疗", "重度", false,
                "冠心病由冠状动脉狭窄或闭塞引起，可导致心肌缺血和心肌梗死。",
                "冠心病病历样例", "活动后胸痛加重", "冠状动脉粥样硬化性心脏病", "药物治疗并评估介入指征");
        seedDisease(diseases, medicalCases, "ICD-J18", "肺炎", "Pneumonia", "呼吸系统疾病", "呼吸科",
                "细菌、病毒或其他病原体感染", "发热、咳嗽、咳痰、呼吸困难", "免疫力下降、吸烟、慢性肺病",
                "接种疫苗、保持通风、及时治疗呼吸道感染", "抗感染治疗、氧疗、对症支持", "中度", true,
                "肺炎是肺实质感染性炎症，需根据病原体和严重程度选择治疗。",
                "肺炎病历样例", "发热咳嗽三天", "社区获得性肺炎", "抗生素治疗联合氧疗");
        seedDisease(diseases, medicalCases, "ICD-K29", "胃炎", "Gastritis", "消化系统疾病", "消化科",
                "胃黏膜炎症", "上腹痛、恶心、反酸、食欲下降", "幽门螺杆菌感染、饮酒、药物刺激",
                "规律饮食、减少刺激性食物、避免滥用止痛药", "抑酸、保护胃黏膜、根除幽门螺杆菌", "轻度", false,
                "胃炎是胃黏膜炎症性疾病，常与饮食、感染和药物刺激相关。",
                "胃炎病历样例", "反复上腹痛伴反酸", "慢性胃炎", "抑酸治疗和饮食干预");
    }

    private void seedDisease(
            DiseaseInfoRepository diseases,
            MedicalCaseRepository medicalCases,
            String code,
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
            String description,
            String caseTitle,
            String chiefComplaint,
            String diagnosis,
            String treatmentGiven) {
        DiseaseInfoEntity disease = diseases.findTop20ByDiseaseNameContainingIgnoreCaseOrDiseaseCodeContainingIgnoreCaseOrderByCreatedAtDesc(name, code)
                .stream()
                .filter(row -> code.equals(row.getDiseaseCode()))
                .findFirst()
                .orElseGet(() -> {
                    DiseaseInfoEntity row = new DiseaseInfoEntity();
                    row.setDiseaseCode(code);
                    row.setDiseaseName(name);
                    row.setDiseaseNameEn(englishName);
                    row.setDiseaseCategory(category);
                    row.setDepartment(department);
                    row.setPathogen(pathogen);
                    row.setSymptoms(symptoms);
                    row.setRiskFactors(riskFactors);
                    row.setPreventionMeasures(prevention);
                    row.setTreatmentPlan(treatment);
                    row.setSeverityLevel(severity);
                    row.setInfectious(infectious);
                    row.setDescription(description);
                    row.setPrognosis("规范治疗和长期管理后多数患者可稳定控制。");
                    LocalDateTime now = LocalDateTime.now();
                    row.setCreatedAt(now);
                    row.setUpdatedAt(now);
                    return diseases.save(row);
                });
        String dataSource = "系统演示-" + code;
        if (!medicalCases.existsByDataSource(dataSource)) {
            MedicalCaseEntity medicalCase = new MedicalCaseEntity();
            medicalCase.setDiseaseId(disease.getId());
            medicalCase.setCaseTitle(caseTitle);
            medicalCase.setHospital("湖北工业大学教学演示医院");
            medicalCase.setPatientAge(62);
            medicalCase.setPatientGender("男");
            medicalCase.setSeverityLevel(severity);
            medicalCase.setChiefComplaint(chiefComplaint);
            medicalCase.setDiagnosis(diagnosis);
            medicalCase.setTreatmentGiven(treatmentGiven);
            medicalCase.setTreatmentOutcome("症状改善，建议规律随访。");
            medicalCase.setDataSource(dataSource);
            LocalDateTime now = LocalDateTime.now();
            medicalCase.setCreatedAt(now);
            medicalCase.setUpdatedAt(now);
            medicalCases.save(medicalCase);
        }
    }
}
