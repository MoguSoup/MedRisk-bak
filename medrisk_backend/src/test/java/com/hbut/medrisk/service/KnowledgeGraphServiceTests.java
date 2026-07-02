package com.hbut.medrisk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbut.medrisk.entity.KnowledgeDocumentEntity;
import com.hbut.medrisk.entity.KnowledgeGraphJobEntity;
import com.hbut.medrisk.entity.UserEntity;
import com.hbut.medrisk.repository.KnowledgeDocumentRepository;
import com.hbut.medrisk.repository.KnowledgeGraphJobRepository;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class KnowledgeGraphServiceTests {
    private final KnowledgeGraphStore graphStore = mock(KnowledgeGraphStore.class);
    private final KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
    private final KnowledgeGraphJobRepository jobs = mock(KnowledgeGraphJobRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final KnowledgeGraphService service = new KnowledgeGraphService(graphStore, documents, jobs, auditService);

    KnowledgeGraphServiceTests() {
        when(jobs.save(any(KnowledgeGraphJobEntity.class))).thenAnswer(invocation -> {
            KnowledgeGraphJobEntity job = invocation.getArgument(0);
            if (job.getId() == null) {
                job.setId(100L);
            }
            return job;
        });
    }

    @Test
    void incrementalIncludesFailedDocuments() {
        KnowledgeDocumentEntity pending = document(1L, "未构建文档", "未构建");
        KnowledgeDocumentEntity failed = document(2L, "失败文档", "构建失败");
        when(documents.findByGraphStatusInOrderByCreatedAtAsc(List.of("未构建", "构建失败"))).thenReturn(List.of(pending, failed));
        when(graphStore.mergeTriplets(anyLong(), anyString(), isNull(), isNull(), isNull(), anyString(), anyList()))
                .thenReturn(new KnowledgeGraphStore.GraphWriteResult(60, 90));

        Map<String, Object> result = service.incremental(admin());

        assertThat(result.get("status")).isEqualTo("构建成功");
        assertThat(pending.getGraphStatus()).isEqualTo("已构建");
        assertThat(failed.getGraphStatus()).isEqualTo("已构建");
        verify(documents).findByGraphStatusInOrderByCreatedAtAsc(List.of("未构建", "构建失败"));
    }

    @Test
    void rebuildContinuesWhenOneDocumentFails() {
        KnowledgeDocumentEntity first = document(1L, "第一篇", "已构建");
        KnowledgeDocumentEntity second = document(2L, "第二篇", "已构建");
        KnowledgeDocumentEntity third = document(3L, "第三篇", "已构建");
        when(documents.findAllNewest()).thenReturn(List.of(first, second, third));
        when(graphStore.clearMedRisk()).thenReturn(new KnowledgeGraphStore.GraphWriteResult(10, 12));
        when(graphStore.mergeTriplets(anyLong(), anyString(), isNull(), isNull(), isNull(), anyString(), anyList()))
                .thenReturn(new KnowledgeGraphStore.GraphWriteResult(60, 90))
                .thenThrow(new IllegalStateException("simulated neo4j write failure"))
                .thenReturn(new KnowledgeGraphStore.GraphWriteResult(70, 100));

        Map<String, Object> result = service.rebuild(admin());

        assertThat(result.get("status")).isEqualTo("部分成功");
        assertThat(first.getGraphStatus()).isEqualTo("已构建");
        assertThat(second.getGraphStatus()).isEqualTo("构建失败");
        assertThat(third.getGraphStatus()).isEqualTo("已构建");
        assertThat(result.get("message").toString()).contains("失败 1 个");
    }

    @Test
    void extractionCreatesAtLeastFiftyDocumentScopedNodes() {
        KnowledgeDocumentEntity row = document(9L, "糖尿病护理路径", "未构建");
        row.setSummary("糖尿病患者需要血糖监测、饮食干预、运动管理和胰岛素治疗。");
        row.setContent("""
                糖尿病常见症状包括多饮、多尿、乏力和体重下降。
                风险因素包括肥胖、家族史、高糖饮食和缺乏运动。
                检查包括空腹血糖、糖化血红蛋白、尿常规和肾功能。
                治疗需要生活方式干预、降糖药物、胰岛素治疗和长期随访。
                """);

        List<KnowledgeGraphStore.Triplet> triplets = service.extractTriplets(row);

        assertThat(distinctNodeCount(row.getTitle(), triplets)).isGreaterThanOrEqualTo(50);
        assertThat(triplets).anyMatch(triplet -> triplet.tailType().equals("TextUnit"));
        assertThat(triplets).anyMatch(triplet -> triplet.tailType().equals("RiskFactor"));
    }

    @Test
    void healthReturnsDownWhenNeo4jIsUnavailable() {
        KnowledgeGraphStore unavailable = new KnowledgeGraphStore("bolt://127.0.0.1:1", "neo4j", "test", "neo4j");
        try {
            Map<String, Object> health = unavailable.health();
            assertThat(health.get("connected")).isEqualTo(false);
            assertThat(health.get("status")).isEqualTo("DOWN");
        } finally {
            unavailable.close();
        }
    }

    private UserEntity admin() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername("admin");
        user.setName("管理员");
        user.setRole("ADMIN");
        user.setStatus("ACTIVE");
        return user;
    }

    private KnowledgeDocumentEntity document(Long id, String title, String status) {
        KnowledgeDocumentEntity row = new KnowledgeDocumentEntity();
        row.setId(id);
        row.setTitle(title);
        row.setOriginalFileName(title + ".txt");
        row.setFileType("txt");
        row.setFileSize(100L);
        row.setFileBucket("knowledge-documents");
        row.setFileObjectKey(title + ".txt");
        row.setContent(title + " 包含疾病风险、症状、检查、治疗和随访建议。");
        row.setSummary(title + " 摘要");
        row.setGraphStatus(status);
        row.setUploadedBy(1L);
        row.setVisibility("PUBLIC");
        row.setCreatedAt(LocalDateTime.now());
        row.setUpdatedAt(LocalDateTime.now());
        return row;
    }

    private int distinctNodeCount(String title, List<KnowledgeGraphStore.Triplet> triplets) {
        Set<String> nodes = new HashSet<>();
        nodes.add("Document:" + title);
        for (KnowledgeGraphStore.Triplet triplet : triplets) {
            nodes.add(triplet.headType() + ":" + triplet.head());
            nodes.add(triplet.tailType() + ":" + triplet.tail());
        }
        return nodes.size();
    }
}
