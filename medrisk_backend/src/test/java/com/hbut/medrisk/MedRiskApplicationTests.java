package com.hbut.medrisk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "medrisk.neo4j.uri=bolt://127.0.0.1:1",
        "medrisk.mail.skip-send=true",
        "medrisk.mail.test-code=123456"
})
@AutoConfigureMockMvc
class MedRiskApplicationTests {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void healthEndpointReturnsUp() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    void auditLogsIncludeLoginClientIp() throws Exception {
        MvcResult forwardedLogin = mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", "unknown, 203.0.113.9, 10.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "admin", "password", "123456"))))
                .andExpect(status().isOk())
                .andReturn();
        String adminToken = objectMapper.readTree(forwardedLogin.getResponse().getContentAsString()).path("data").path("token").asText();

        mockMvc.perform(post("/api/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.7");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "doctor", "password", "123456"))))
                .andExpect(status().isOk());

        MvcResult auditResult = mockMvc.perform(get("/api/admin/audit-logs").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode logs = objectMapper.readTree(auditResult.getResponse().getContentAsString()).path("data");
        assertThat(hasLoginIp(logs, "admin", "203.0.113.9")).isTrue();
        assertThat(hasLoginIp(logs, "doctor", "198.51.100.7")).isTrue();
    }

    @Test
    void emailCodeRegisterAndPasswordResetFlowWorks() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "email_flow_no_code",
                                "email", "email_flow_no_code@medrisk.local",
                                "name", "邮箱验证码用户",
                                "role", "PATIENT",
                                "password", "123456"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/auth/register/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "email_flow@medrisk.local"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "email_flow",
                                "email", "email_flow@medrisk.local",
                                "name", "邮箱验证码用户",
                                "role", "PATIENT",
                                "password", "123456",
                                "emailCode", "123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isString());

        mockMvc.perform(post("/api/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "email_flow@medrisk.local"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "email_flow@medrisk.local",
                                "emailCode", "123456",
                                "newPassword", "654321"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "email_flow", "password", "123456"))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "email_flow", "password", "654321"))))
                .andExpect(status().isOk());
    }

    @Test
    void auditLogsIncludeClientIpForBusinessOperations() throws Exception {
        String adminToken = login("admin", "123456");
        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Forwarded-For", "203.0.113.77")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "ip_audit_user",
                                "email", "ip_audit_user@medrisk.local",
                                "name", "IP 审计用户",
                                "phone", "13800000000",
                                "role", "PATIENT",
                                "status", "ACTIVE",
                                "password", "123456"))))
                .andExpect(status().isOk());

        MvcResult auditResult = mockMvc.perform(get("/api/admin/audit-logs").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode logs = objectMapper.readTree(auditResult.getResponse().getContentAsString()).path("data");
        assertThat(hasActionIp(logs, "CREATE_USER", "203.0.113.77")).isTrue();
    }

    @Test
    void loginPredictReportAndAdminFlowWorks() throws Exception {
        String adminToken = login("admin", "123456");

        MvcResult prediction = mockMvc.perform(post("/api/predict/heart")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "patientName", "演示患者",
                                "age", 62,
                                "bmi", 29.5,
                                "glucose", 8.2,
                                "bloodPressure", 152,
                                "cholesterol", 6.3,
                                "smoker", true,
                                "chestPain", true,
                                "maxHeartRate", 118))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.diseaseType").value("heart"))
                .andExpect(jsonPath("$.data.recordId").isNumber())
                .andReturn();

        long recordId = objectMapper.readTree(prediction.getResponse().getContentAsString())
                .path("data")
                .path("recordId")
                .asLong();

        mockMvc.perform(get("/api/history/predictions").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].recordId").value(recordId));

        MvcResult report = mockMvc.perform(post("/api/reports/generate/" + recordId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andReturn();

        long reportId = objectMapper.readTree(report.getResponse().getContentAsString())
                .path("data")
                .path("id")
                .asLong();

        MvcResult pdf = mockMvc.perform(get("/api/reports/" + reportId + "/download")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn();
        assertThat(pdf.getResponse().getContentAsByteArray()).startsWith("%PDF".getBytes());

        mockMvc.perform(get("/api/admin/models").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(5));
    }

    @Test
    void adminTrainingManagementFlowPersistsRecordsAndRejectsPatient() throws Exception {
        String adminToken = login("admin", "123456");
        String patientToken = login("patient", "123456");

        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(get("/api/admin/datasets").header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        MockMultipartFile datasetFile = new MockMultipartFile(
                "file",
                "heart-training.csv",
                "text/csv",
                sampleCsv().getBytes());

        MvcResult datasetResult = mockMvc.perform(multipart("/api/admin/datasets")
                        .file(datasetFile)
                        .param("name", "心脏病训练样例")
                        .param("diseaseType", "heart")
                        .param("description", "后端测试用结构化 CSV")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VALID"))
                .andExpect(jsonPath("$.data.sampleCount").value(12))
                .andExpect(jsonPath("$.data.featureColumns[0]").value("age"))
                .andReturn();

        long datasetId = objectMapper.readTree(datasetResult.getResponse().getContentAsString())
                .path("data")
                .path("id")
                .asLong();

        MockMultipartFile invalidFile = new MockMultipartFile(
                "file",
                "invalid.csv",
                "text/csv",
                "age,bmi\n60,28\n61,29\n62,30\n63,31\n64,32\n65,33\n66,34\n67,35\n".getBytes());

        mockMvc.perform(multipart("/api/admin/datasets")
                        .file(invalidFile)
                        .param("name", "缺少标签列")
                        .param("diseaseType", "heart")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INVALID"))
                .andExpect(jsonPath("$.data.validationMessage").value("数据集必须包含 label 列"));

        MvcResult jobResult = mockMvc.perform(post("/api/admin/training-jobs")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "datasetId", datasetId,
                                "evaluationDatasetId", datasetId,
                                "modelName", "XGBoost 单元测试模型",
                                "epochs", 3,
                                "learningRate", 0.1,
                                "testSize", 0.25,
                                "hyperparameters", Map.of(
                                        "nEstimators", 12,
                                        "maxDepth", 4,
                                        "learningRate", 0.12,
                                        "subsample", 0.85,
                                        "colsampleBytree", 0.8,
                                        "regLambda", 1.5,
                                        "minChildWeight", 2,
                                        "testSize", 0.25)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trainStatus").value("训练失败"))
                .andExpect(jsonPath("$.data.datasetId").value(datasetId))
                .andExpect(jsonPath("$.data.evaluationDatasetId").value(datasetId))
                .andExpect(jsonPath("$.data.evaluationDatasetName").value("心脏病训练样例"))
                .andExpect(jsonPath("$.data.hyperparameters.nEstimators").value(12))
                .andExpect(jsonPath("$.data.hyperparameters.maxDepth").value(4))
                .andReturn();

        long jobId = objectMapper.readTree(jobResult.getResponse().getContentAsString())
                .path("data")
                .path("id")
                .asLong();

        mockMvc.perform(get("/api/admin/training-jobs/" + jobId + "/status")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(jobId));

        MvcResult feedbackResult = mockMvc.perform(post("/api/admin/model-feedback")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "problemType", "训练数据",
                                "priority", "中",
                                "status", "待处理",
                                "content", "样例数据偏少，需要补充真实脱敏数据。"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("样例数据偏少，需要补充真实脱敏数据。"))
                .andReturn();

        long feedbackId = objectMapper.readTree(feedbackResult.getResponse().getContentAsString())
                .path("data")
                .path("id")
                .asLong();

        mockMvc.perform(put("/api/admin/model-feedback/" + feedbackId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "problemType", "训练数据",
                                "priority", "高",
                                "status", "处理中",
                                "content", "已确认需要补充分层样本。"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.priority").value("高"))
                .andExpect(jsonPath("$.data.status").value("处理中"));

        mockMvc.perform(delete("/api/admin/model-feedback/" + feedbackId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(true));
    }

    @Test
    void adminUserManagementAndDisabledLoginFlowWorks() throws Exception {
        String adminToken = login("admin", "123456");

        MvcResult created = mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "managed_user",
                                "email", "managed_user@medrisk.local",
                                "name", "受管用户",
                                "phone", "13800000001",
                                "role", "PATIENT",
                                "status", "ACTIVE",
                                "password", "123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("managed_user"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn();
        long userId = objectMapper.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(put("/api/admin/users/" + userId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "managed_user",
                                "email", "managed_user@medrisk.local",
                                "name", "受管医生",
                                "phone", "13800000002",
                                "role", "DOCTOR",
                                "status", "ACTIVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("DOCTOR"))
                .andExpect(jsonPath("$.data.phone").value("13800000002"));

        mockMvc.perform(post("/api/admin/users/" + userId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "DISABLED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "managed_user", "password", "123456"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(post("/api/admin/users/" + userId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "ACTIVE"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/users/" + userId + "/reset-password")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("password", "123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(userId));

        login("managed_user", "123456");

        mockMvc.perform(get("/api/admin/console/summary").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userCount").isNumber());

        mockMvc.perform(get("/api/admin/visualization").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.userCount").isNumber());

        mockMvc.perform(delete("/api/admin/users/" + userId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(true));
    }

    @Test
    void doctorCanSeeAllPredictionsButPatientOnlyOwnRecords() throws Exception {
        String patientToken = login("patient", "123456");
        String otherToken = register("scope_patient", "scope_patient@medrisk.local", "范围患者", "PATIENT");
        String doctorToken = login("doctor", "123456");

        long ownRecordId = createPrediction(patientToken, "演示患者本人");
        long otherRecordId = createPrediction(otherToken, "范围患者");

        JsonNode patientHistory = objectMapper.readTree(mockMvc.perform(get("/api/history/predictions")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("data");
        assertThat(recordIds(patientHistory)).contains(ownRecordId).doesNotContain(otherRecordId);

        JsonNode doctorHistory = objectMapper.readTree(mockMvc.perform(get("/api/history/predictions")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("data");
        assertThat(recordIds(doctorHistory)).contains(ownRecordId, otherRecordId);

        mockMvc.perform(get("/api/predict/explain/" + otherRecordId)
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(get("/api/doctor/console/summary").header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.predictionCount").isNumber());
    }

    @Test
    void graphRagKnowledgeDiseaseCaseAndConversationFlowWorks() throws Exception {
        String adminToken = login("admin", "123456");
        String doctorToken = login("doctor", "123456");
        String patientToken = login("patient", "123456");

        mockMvc.perform(get("/api/documents").header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/documents").header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        MockMultipartFile rejectedDocument = new MockMultipartFile(
                "file",
                "patient-doc.txt",
                "text/plain",
                "patient upload should be rejected".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/admin/documents")
                        .file(rejectedDocument)
                        .param("title", "Patient Upload")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isForbidden());

        MockMultipartFile document = new MockMultipartFile(
                "file",
                "test-disease.txt",
                "text/plain",
                "Test Disease symptoms include fever and cough. Treatment includes hydration."
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        MvcResult documentResult = mockMvc.perform(multipart("/api/admin/documents")
                        .file(document)
                        .param("title", "Test Disease Knowledge")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.title").value("Test Disease Knowledge"))
                .andExpect(jsonPath("$.data.fileType").value("txt"))
                .andReturn();
        long documentId = objectMapper.readTree(documentResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(get("/api/documents/" + documentId).header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/documents/" + documentId).header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(documentId));

        MvcResult downloaded = mockMvc.perform(get("/api/documents/" + documentId + "/download")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(downloaded.getResponse().getContentAsString()).contains("Test Disease symptoms");

        mockMvc.perform(get("/api/admin/knowledge-graph/health").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected").exists());

        mockMvc.perform(get("/api/admin/knowledge-graph/visualization").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodes").isArray())
                .andExpect(jsonPath("$.data.relationships").isArray());

        MockMultipartFile diseaseImage = new MockMultipartFile(
                "image",
                "disease.jpg",
                "image/jpeg",
                new byte[] {1, 2, 3, 4});
        MvcResult diseaseResult = mockMvc.perform(multipart("/api/admin/diseases")
                        .file(diseaseImage)
                        .param("diseaseCode", "TEST-KG-001")
                        .param("diseaseName", "Test Disease")
                        .param("department", "Test Department")
                        .param("symptoms", "fever,cough")
                        .param("treatmentPlan", "hydration and monitoring")
                        .param("infectious", "false")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.diseaseCode").value("TEST-KG-001"))
                .andExpect(jsonPath("$.data.imageUrl").isString())
                .andReturn();
        long diseaseId = objectMapper.readTree(diseaseResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(get("/api/diseases").param("keyword", "Test Disease").header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/diseases").param("keyword", "Test Disease").header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].diseaseName").value("Test Disease"));

        MockMultipartFile caseImage = new MockMultipartFile(
                "images",
                "case.jpg",
                "image/jpeg",
                new byte[] {5, 6, 7, 8});
        MvcResult caseResult = mockMvc.perform(multipart("/api/admin/medical-cases")
                        .file(caseImage)
                        .param("diseaseId", String.valueOf(diseaseId))
                        .param("caseTitle", "Test Disease Case")
                        .param("hospital", "Test Hospital")
                        .param("patientAge", "45")
                        .param("diagnosis", "Test Disease")
                        .param("treatmentGiven", "hydration and monitoring")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.caseTitle").value("Test Disease Case"))
                .andExpect(jsonPath("$.data.images.length()").value(1))
                .andReturn();
        long caseId = objectMapper.readTree(caseResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(get("/api/medical-cases/" + caseId).header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/medical-cases/" + caseId).header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.diseaseName").value("Test Disease"));

        MvcResult conversationResult = mockMvc.perform(post("/api/conversations")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "Knowledge QA"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversation.id").isNumber())
                .andReturn();
        long conversationId = objectMapper.readTree(conversationResult.getResponse().getContentAsString())
                .path("data")
                .path("conversation")
                .path("id")
                .asLong();

        MockMultipartFile questionImage = new MockMultipartFile(
                "image",
                "question.jpg",
                "image/jpeg",
                new byte[] {9, 10, 11, 12});
        mockMvc.perform(multipart("/api/conversations/" + conversationId + "/messages")
                        .file(questionImage)
                        .param("question", "Test Disease treatment")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer").isString())
                .andExpect(jsonPath("$.data.imageUrl").isString())
                .andExpect(jsonPath("$.data.diseaseInfoMatches[0].diseaseName").value("Test Disease"));
    }

    @Test
    void qaBlocksOutOfScopeQuestionsBeforeExternalModelCall() throws Exception {
        String patientToken = login("patient", "123456");
        MvcResult conversationResult = mockMvc.perform(post("/api/conversations")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "范围测试"))))
                .andExpect(status().isOk())
                .andReturn();
        long conversationId = objectMapper.readTree(conversationResult.getResponse().getContentAsString())
                .path("data")
                .path("conversation")
                .path("id")
                .asLong();

        mockMvc.perform(multipart("/api/conversations/" + conversationId + "/messages")
                        .param("question", "帮我写一个股票交易策略")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usedModel").value("policy-guard"))
                .andExpect(jsonPath("$.data.provider").value("medrisk-policy"))
                .andExpect(jsonPath("$.data.fallbackUsed").value(true))
                .andExpect(jsonPath("$.data.answer").value(org.hamcrest.Matchers.containsString("本系统仅回答 MedRisk 医疗平台")));
    }

    @Test
    void dataSeedStatusAndDraftVisibilityAreRoleSeparated() throws Exception {
        String adminToken = login("admin", "123456");
        String doctorToken = login("doctor", "123456");
        String patientToken = login("patient", "123456");

        mockMvc.perform(get("/api/admin/data-seeds/status").header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isForbidden());

        MvcResult seedStatus = mockMvc.perform(get("/api/admin/data-seeds/status").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sources").isArray())
                .andReturn();
        JsonNode seedCounts = objectMapper.readTree(seedStatus.getResponse().getContentAsString()).path("data").path("counts");
        assertThat(seedCounts.path("diseases").asInt()).isGreaterThanOrEqualTo(50);
        assertThat(seedCounts.path("documents").asInt()).isGreaterThanOrEqualTo(100);
        assertThat(seedCounts.path("medicalCases").asInt()).isGreaterThanOrEqualTo(50);
        assertThat(seedCounts.path("datasets").asInt()).isGreaterThanOrEqualTo(5);

        mockMvc.perform(post("/api/admin/data-seeds/import").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));

        MvcResult draftDisease = mockMvc.perform(multipart("/api/doctor/diseases")
                        .param("diseaseCode", "DRAFT-DOCTOR-001")
                        .param("diseaseName", "医生草稿疾病")
                        .param("department", "测试科室")
                        .param("symptoms", "草稿症状")
                        .param("treatmentPlan", "草稿治疗")
                        .param("infectious", "false")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visibility").value("DRAFT"))
                .andReturn();
        long draftDiseaseId = objectMapper.readTree(draftDisease.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(get("/api/diseases/" + draftDiseaseId).header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/diseases/" + draftDiseaseId).header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.diseaseName").value("医生草稿疾病"));

        MockMultipartFile draftDoc = new MockMultipartFile(
                "file",
                "doctor-draft.txt",
                "text/plain",
                "doctor only draft content".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        MvcResult draftDocument = mockMvc.perform(multipart("/api/doctor/documents")
                        .file(draftDoc)
                        .param("title", "医生草稿文档")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visibility").value("DRAFT"))
                .andReturn();
        long draftDocumentId = objectMapper.readTree(draftDocument.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(get("/api/documents/" + draftDocumentId).header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/documents/" + draftDocumentId).header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("医生草稿文档"));
    }

    @Test
    void currentUserCanManageProfileAvatarAndPassword() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String username = "self" + suffix;
        String token = register(username, username + "@medrisk.local", "自助用户", "PATIENT");

        mockMvc.perform(put("/api/auth/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", username + "-new@medrisk.local",
                                "name", "自助用户更新",
                                "phone", "13800000000",
                                "role", "ADMIN",
                                "status", "DISABLED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("自助用户更新"))
                .andExpect(jsonPath("$.data.email").value(username + "-new@medrisk.local"))
                .andExpect(jsonPath("$.data.phone").value("13800000000"))
                .andExpect(jsonPath("$.data.role").value("PATIENT"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        MockMultipartFile avatar = new MockMultipartFile(
                "avatar",
                "avatar.png",
                "image/png",
                new byte[] {1, 2, 3, 4});
        mockMvc.perform(multipart("/api/auth/me/avatar")
                        .file(avatar)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").isString());

        mockMvc.perform(post("/api/auth/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "oldPassword", "wrong-password",
                                "newPassword", "newpass123"))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "oldPassword", "123456",
                                "newPassword", "newpass123"))))
                .andExpect(status().isOk());

        login(username, "newpass123");
    }

    private String sampleCsv() {
        return """
                age,bmi,glucose,bloodPressure,cholesterol,smoker,chestPain,maxHeartRate,label
                45,23.1,5.1,118,4.8,0,0,165,0
                51,26.4,5.8,124,5.2,0,0,158,0
                57,28.8,6.5,139,5.9,1,0,145,0
                61,30.1,7.4,146,6.2,1,1,132,1
                66,31.7,8.2,154,6.8,1,1,120,1
                70,29.4,7.9,160,6.5,0,1,118,1
                39,22.5,4.9,112,4.6,0,0,172,0
                48,24.7,5.3,121,4.9,0,0,160,0
                59,27.9,6.8,142,5.8,1,0,140,0
                63,32.5,8.9,158,7.1,1,1,115,1
                68,33.0,9.1,166,7.4,1,1,110,1
                72,30.7,8.5,162,6.9,0,1,112,1
                """;
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isString())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("token").asText();
    }

    private boolean hasLoginIp(JsonNode logs, String resourceId, String clientIp) {
        for (JsonNode log : logs) {
            if ("LOGIN".equals(log.path("action").asText())
                    && clientIp.equals(log.path("clientIp").asText())
                    && resourceIdMatches(log.path("resourceId").asText(), resourceId)) {
                return true;
            }
        }
        return false;
    }

    private boolean resourceIdMatches(String resourceId, String username) {
        if ("admin".equals(username)) return "1".equals(resourceId);
        if ("doctor".equals(username)) return "2".equals(resourceId);
        if ("patient".equals(username)) return "3".equals(resourceId);
        return false;
    }

    private boolean hasActionIp(JsonNode logs, String action, String clientIp) {
        for (JsonNode log : logs) {
            if (action.equals(log.path("action").asText()) && clientIp.equals(log.path("clientIp").asText())) {
                return true;
            }
        }
        return false;
    }

    private String register(String username, String email, String name, String role) throws Exception {
        mockMvc.perform(post("/api/auth/register/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isOk());
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "email", email,
                                "name", name,
                                "role", role,
                                "password", "123456",
                                "emailCode", "123456"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("token").asText();
    }

    private long createPrediction(String token, String patientName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/predict/heart")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "patientName", patientName,
                                "age", 68,
                                "bmi", 31.5,
                                "glucose", 8.5,
                                "bloodPressure", 158,
                                "cholesterol", 6.9,
                                "smoker", true,
                                "chestPain", true,
                                "maxHeartRate", 116))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("recordId").asLong();
    }

    private java.util.List<Long> recordIds(JsonNode array) {
        java.util.List<Long> ids = new java.util.ArrayList<>();
        array.forEach(node -> ids.add(node.path("recordId").asLong()));
        return ids;
    }
}
