package com.hbut.medrisk.service;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeGraphStore {
    private static final String SOURCE = "MedRisk";
    private static final long UNAVAILABLE_BACKOFF_MILLIS = 5000L;

    private final Driver driver;
    private final String database;
    private volatile long unavailableUntilMillis = 0L;

    public KnowledgeGraphStore(
            @Value("${medrisk.neo4j.uri}") String uri,
            @Value("${medrisk.neo4j.username}") String username,
            @Value("${medrisk.neo4j.password}") String password,
            @Value("${medrisk.neo4j.database}") String database) {
        this.driver = GraphDatabase.driver(uri, AuthTokens.basic(username, password), Config.builder()
                .withConnectionTimeout(2, TimeUnit.SECONDS)
                .withConnectionAcquisitionTimeout(2, TimeUnit.SECONDS)
                .withMaxTransactionRetryTime(1, TimeUnit.SECONDS)
                .withMaxConnectionPoolSize(8)
                .build());
        this.database = database == null ? "" : database.trim();
    }

    public Map<String, Object> health() {
        try (Session session = session()) {
            driver.verifyConnectivity();
            String value = session.executeRead(tx -> tx.run("RETURN 'UP' AS status").single().get("status").asString());
            unavailableUntilMillis = 0L;
            return Map.of("connected", true, "status", value, "database", database.isBlank() ? "default" : database);
        } catch (Exception ex) {
            markUnavailable();
            return Map.of("connected", false, "status", "DOWN", "message", ex.getMessage());
        }
    }

    public GraphWriteResult clearMedRisk() {
        assertAvailable();
        try (Session session = session()) {
            return session.executeWrite(tx -> {
                long relationships = tx.run("""
                        MATCH ()-[r]-()
                        WHERE r.medriskSource = $source
                        WITH count(r) AS count
                        MATCH ()-[r2]-()
                        WHERE r2.medriskSource = $source
                        DELETE r2
                        RETURN count
                        """, Map.of("source", SOURCE)).single().get("count").asLong();
                long nodes = tx.run("""
                        MATCH (n)
                        WHERE n.medriskSource = $source
                        WITH count(n) AS count
                        MATCH (n2)
                        WHERE n2.medriskSource = $source
                        DETACH DELETE n2
                        RETURN count
                        """, Map.of("source", SOURCE)).single().get("count").asLong();
                return new GraphWriteResult((int) nodes, (int) relationships);
            });
        }
    }

    public GraphWriteResult mergeTriplets(Long documentId, String documentTitle, List<Triplet> triplets) {
        return mergeTriplets(documentId, documentTitle, null, null, null, "PUBLIC", triplets);
    }

    public GraphWriteResult mergeTriplets(
            Long documentId,
            String documentTitle,
            String sourceName,
            String sourceUrl,
            String sourceLicense,
            String visibility,
            List<Triplet> triplets) {
        assertAvailable();
        try (Session session = session()) {
            return session.executeWrite(tx -> {
                int nodes = 0;
                int relationships = 0;
                String documentKey = documentKey(documentId, documentTitle);
                for (Triplet triplet : triplets) {
                    String headType = safeLabel(triplet.headType());
                    String tailType = safeLabel(triplet.tailType());
                    String relation = safeRelationship(triplet.relation());
                    String headKey = nodeKey(documentKey, documentTitle, headType, triplet.head());
                    String tailKey = nodeKey(documentKey, documentTitle, tailType, triplet.tail());
                    tx.run("""
                            MERGE (d:Document {graphKey: $documentKey})
                            SET d.name = $documentTitle,
                                d.type = 'Document',
                                d.medriskSource = $source,
                                d.documentId = $documentId,
                                d.description = $documentTitle,
                                d.sourceName = $sourceName,
                                d.sourceUrl = $sourceUrl,
                                d.sourceLicense = $sourceLicense,
                                d.visibility = $visibility
                            """, params(
                            "documentKey", documentKey,
                            "documentTitle", documentTitle,
                            "source", SOURCE,
                            "documentId", documentId,
                            "sourceName", sourceName,
                            "sourceUrl", sourceUrl,
                            "sourceLicense", sourceLicense,
                            "visibility", visibility));
                    tx.run("""
                            MERGE (h:%s {graphKey: $graphKey})
                            SET h.name = $name,
                                h.type = $type,
                                h.medriskSource = $source,
                                h.documentId = $documentId,
                                h.description = coalesce($description, h.description, ''),
                                h.sourceName = $sourceName,
                                h.sourceUrl = $sourceUrl,
                                h.sourceLicense = $sourceLicense,
                                h.visibility = $visibility
                            """.formatted(headType), params(
                            "graphKey", headKey,
                            "name", triplet.head(),
                            "type", headType,
                            "source", SOURCE,
                            "documentId", documentId,
                            "sourceName", sourceName,
                            "sourceUrl", sourceUrl,
                            "sourceLicense", sourceLicense,
                            "visibility", visibility,
                            "description", triplet.headDescription() == null ? "" : triplet.headDescription()));
                    tx.run("""
                            MERGE (t:%s {graphKey: $graphKey})
                            SET t.name = $name,
                                t.type = $type,
                                t.medriskSource = $source,
                                t.documentId = $documentId,
                                t.description = coalesce($description, t.description, ''),
                                t.sourceName = $sourceName,
                                t.sourceUrl = $sourceUrl,
                                t.sourceLicense = $sourceLicense,
                                t.visibility = $visibility
                            """.formatted(tailType), params(
                            "graphKey", tailKey,
                            "name", triplet.tail(),
                            "type", tailType,
                            "source", SOURCE,
                            "documentId", documentId,
                            "sourceName", sourceName,
                            "sourceUrl", sourceUrl,
                            "sourceLicense", sourceLicense,
                            "visibility", visibility,
                            "description", triplet.tailDescription() == null ? "" : triplet.tailDescription()));
                    tx.run("""
                            MATCH (h:%s {graphKey: $headKey})
                            MATCH (t:%s {graphKey: $tailKey})
                            MERGE (h)-[r:%s]->(t)
                            SET r.medriskSource = $source,
                                r.documentId = $documentId,
                                r.label = $label,
                                r.sourceName = $sourceName,
                                r.sourceUrl = $sourceUrl,
                                r.sourceLicense = $sourceLicense,
                                r.visibility = $visibility
                            """.formatted(headType, tailType, relation), params(
                            "headKey", headKey,
                            "tailKey", tailKey,
                            "source", SOURCE,
                            "documentId", documentId,
                            "sourceName", sourceName,
                            "sourceUrl", sourceUrl,
                            "sourceLicense", sourceLicense,
                            "visibility", visibility,
                            "label", triplet.relationLabel()));
                    if (!headKey.equals(documentKey)) {
                        tx.run("""
                                MATCH (d:Document {graphKey: $documentKey})
                                MATCH (h:%s {graphKey: $headKey})
                                MERGE (h)-[r:RECORDED_IN]->(d)
                                SET r.medriskSource = $source,
                                    r.documentId = $documentId,
                                    r.label = '记录于',
                                    r.sourceName = $sourceName,
                                    r.sourceUrl = $sourceUrl,
                                    r.sourceLicense = $sourceLicense,
                                    r.visibility = $visibility
                                """.formatted(headType), params(
                                "documentKey", documentKey,
                                "headKey", headKey,
                                "source", SOURCE,
                                "documentId", documentId,
                                "sourceName", sourceName,
                                "sourceUrl", sourceUrl,
                                "sourceLicense", sourceLicense,
                                "visibility", visibility));
                    }
                    nodes += 2;
                    relationships += headKey.equals(documentKey) ? 1 : 2;
                }
                return new GraphWriteResult(nodes, relationships);
            });
        }
    }

    public List<Map<String, Object>> search(String keyword, int limit) {
        return search(keyword, limit, List.of("PUBLIC", "DOCTOR_ONLY", "ADMIN_ONLY", "DRAFT"), null);
    }

    public List<Map<String, Object>> search(String keyword, int limit, List<String> visibilities, String sourceName) {
        if (keyword == null || keyword.isBlank() || temporarilyUnavailable()) {
            return List.of();
        }
        try (Session session = session()) {
            return session.executeRead(tx -> tx.run("""
                    MATCH (n)
                    WHERE n.medriskSource = $source AND toLower(n.name) CONTAINS toLower($keyword)
                      AND coalesce(n.visibility, 'PUBLIC') IN $visibilities
                      AND ($sourceName = '' OR coalesce(n.sourceName, '') = $sourceName)
                    OPTIONAL MATCH (n)-[r]-(m)
                    WHERE m.medriskSource = $source AND coalesce(m.visibility, 'PUBLIC') IN $visibilities
                    RETURN id(n) AS id, n.name AS name, coalesce(n.type, labels(n)[0]) AS type,
                           coalesce(n.description, '') AS description,
                           coalesce(n.sourceName, '') AS sourceName,
                           coalesce(n.sourceUrl, '') AS sourceUrl,
                           coalesce(n.visibility, 'PUBLIC') AS visibility,
                           collect({source: n.name, target: m.name, type: type(r), label: coalesce(r.label, type(r))})[0..8] AS relationships
                    LIMIT $limit
                    """, params(
                            "source", SOURCE,
                            "keyword", keyword,
                            "limit", limit,
                            "visibilities", visibilities == null || visibilities.isEmpty() ? List.of("PUBLIC") : visibilities,
                            "sourceName", sourceName == null ? "" : sourceName))
                    .list(this::recordToSearchResult));
        } catch (Exception ex) {
            markUnavailable();
            return List.of();
        }
    }

    public Map<String, Object> visualization(String keyword, List<String> nodeTypes, List<String> relationshipTypes, int limit) {
        return visualization(keyword, nodeTypes, relationshipTypes, List.of("PUBLIC", "DOCTOR_ONLY", "ADMIN_ONLY", "DRAFT"), null, limit);
    }

    public Map<String, Object> visualization(
            String keyword,
            List<String> nodeTypes,
            List<String> relationshipTypes,
            List<String> visibilities,
            String sourceName,
            int limit) {
        if (temporarilyUnavailable()) {
            return emptyGraph("Neo4j is temporarily unavailable");
        }
        try (Session session = session()) {
            return session.executeRead(tx -> {
                Map<String, Object> params = new LinkedHashMap<>();
                params.put("source", SOURCE);
                params.put("keyword", keyword == null ? "" : keyword.trim());
                params.put("nodeTypes", nodeTypes == null ? List.of() : nodeTypes);
                params.put("visibilities", visibilities == null || visibilities.isEmpty() ? List.of("PUBLIC") : visibilities);
                params.put("sourceName", sourceName == null ? "" : sourceName.trim());
                params.put("limit", limit);
                List<Map<String, Object>> seedNodes = tx.run("""
                        MATCH (n)
                        WHERE n.medriskSource = $source
                          AND ($keyword = '' OR toLower(n.name) CONTAINS toLower($keyword))
                          AND (size($nodeTypes) = 0 OR coalesce(n.type, labels(n)[0]) IN $nodeTypes)
                          AND coalesce(n.visibility, 'PUBLIC') IN $visibilities
                          AND ($sourceName = '' OR coalesce(n.sourceName, '') = $sourceName)
                        OPTIONAL MATCH (n)--(m)
                        WITH n, count(m) AS degree
                        RETURN id(n) AS id, n.name AS name, coalesce(n.type, labels(n)[0]) AS type,
                               coalesce(n.description, '') AS description,
                               coalesce(n.sourceName, '') AS sourceName,
                               coalesce(n.sourceUrl, '') AS sourceUrl,
                               coalesce(n.visibility, 'PUBLIC') AS visibility,
                               degree
                        ORDER BY degree DESC
                        LIMIT $limit
                        """, params).list(record -> orderedMap(
                        "id", String.valueOf(record.get("id").asLong()),
                        "name", record.get("name").asString(""),
                        "type", record.get("type").asString("Entity"),
                        "description", record.get("description").asString(""),
                        "sourceName", record.get("sourceName").asString(""),
                        "sourceUrl", record.get("sourceUrl").asString(""),
                        "visibility", record.get("visibility").asString("PUBLIC"),
                        "degree", record.get("degree").asLong(0)));
                Map<String, Map<String, Object>> nodeMap = new LinkedHashMap<>();
                seedNodes.forEach(node -> nodeMap.put(String.valueOf(node.get("id")), node));
                List<String> ids = seedNodes.stream().map(node -> String.valueOf(node.get("id"))).toList();
                Map<String, Object> edgeParams = new LinkedHashMap<>();
                edgeParams.put("source", SOURCE);
                edgeParams.put("ids", ids.stream().map(Long::parseLong).toList());
                edgeParams.put("relationshipTypes", relationshipTypes == null ? List.of() : relationshipTypes);
                edgeParams.put("visibilities", visibilities == null || visibilities.isEmpty() ? List.of("PUBLIC") : visibilities);
                edgeParams.put("sourceName", sourceName == null ? "" : sourceName.trim());
                edgeParams.put("edgeLimit", Math.max(180, Math.min(limit * 4, 600)));
                List<Map<String, Object>> relationships = tx.run("""
                        MATCH (a)-[r]->(b)
                        WHERE a.medriskSource = $source AND b.medriskSource = $source
                          AND (id(a) IN $ids OR id(b) IN $ids)
                          AND (size($relationshipTypes) = 0 OR coalesce(r.label, type(r)) IN $relationshipTypes OR type(r) IN $relationshipTypes)
                          AND coalesce(a.visibility, 'PUBLIC') IN $visibilities
                          AND coalesce(b.visibility, 'PUBLIC') IN $visibilities
                          AND coalesce(r.visibility, 'PUBLIC') IN $visibilities
                          AND ($sourceName = ''
                               OR coalesce(a.sourceName, '') = $sourceName
                               OR coalesce(b.sourceName, '') = $sourceName
                               OR coalesce(r.sourceName, '') = $sourceName)
                        RETURN id(a) AS source, id(b) AS target, type(r) AS type, coalesce(r.label, type(r)) AS label,
                               coalesce(r.visibility, 'PUBLIC') AS visibility,
                               coalesce(r.sourceName, '') AS sourceName,
                               a.name AS sourceNodeName,
                               coalesce(a.type, labels(a)[0]) AS sourceNodeType,
                               coalesce(a.description, '') AS sourceNodeDescription,
                               coalesce(a.sourceName, '') AS sourceNodeSourceName,
                               coalesce(a.sourceUrl, '') AS sourceNodeSourceUrl,
                               coalesce(a.visibility, 'PUBLIC') AS sourceNodeVisibility,
                               b.name AS targetNodeName,
                               coalesce(b.type, labels(b)[0]) AS targetNodeType,
                               coalesce(b.description, '') AS targetNodeDescription,
                               coalesce(b.sourceName, '') AS targetNodeSourceName,
                               coalesce(b.sourceUrl, '') AS targetNodeSourceUrl,
                               coalesce(b.visibility, 'PUBLIC') AS targetNodeVisibility
                        ORDER BY CASE WHEN id(a) IN $ids AND id(b) IN $ids THEN 0 ELSE 1 END
                        LIMIT $edgeLimit
                        """, edgeParams).list(record -> {
                    putVisualizationNode(nodeMap, record, "source", "sourceNode");
                    putVisualizationNode(nodeMap, record, "target", "targetNode");
                    return orderedMap(
                            "source", String.valueOf(record.get("source").asLong()),
                            "target", String.valueOf(record.get("target").asLong()),
                            "type", record.get("type").asString("RELATED_TO"),
                            "label", record.get("label").asString("相关"),
                            "visibility", record.get("visibility").asString("PUBLIC"),
                            "sourceName", record.get("sourceName").asString(""));
                });
                List<Map<String, Object>> nodes = trimVisualizationNodes(seedNodes, nodeMap, Math.max(120, Math.min(limit * 3, 300)));
                Set<String> retainedNodeIds = new HashSet<>(nodes.stream().map(node -> String.valueOf(node.get("id"))).toList());
                relationships = relationships.stream()
                        .filter(row -> retainedNodeIds.contains(String.valueOf(row.get("source")))
                                && retainedNodeIds.contains(String.valueOf(row.get("target"))))
                        .toList();
                return orderedMap(
                        "nodes", nodes,
                        "relationships", relationships,
                        "nodeTypes", nodes.stream().map(node -> String.valueOf(node.get("type"))).distinct().toList(),
                        "relationshipTypes", relationships.stream().map(row -> String.valueOf(row.get("label"))).distinct().toList(),
                        "sourceNames", nodes.stream().map(node -> String.valueOf(node.get("sourceName"))).filter(value -> !value.isBlank()).distinct().toList(),
                        "visibilities", nodes.stream().map(node -> String.valueOf(node.get("visibility"))).distinct().toList(),
                        "summary", orderedMap("nodeCount", nodes.size(), "relationshipCount", relationships.size()));
            });
        } catch (Exception ex) {
            markUnavailable();
            return emptyGraph(ex.getMessage());
        }
    }

    private void assertAvailable() {
        if (temporarilyUnavailable()) {
            throw new IllegalStateException("Neo4j is temporarily unavailable");
        }
    }

    private boolean temporarilyUnavailable() {
        return System.currentTimeMillis() < unavailableUntilMillis;
    }

    private void markUnavailable() {
        unavailableUntilMillis = System.currentTimeMillis() + UNAVAILABLE_BACKOFF_MILLIS;
    }

    private Map<String, Object> emptyGraph(String message) {
        return orderedMap(
                "nodes", List.of(),
                "relationships", List.of(),
                "nodeTypes", List.of(),
                "relationshipTypes", List.of(),
                "summary", orderedMap("nodeCount", 0, "relationshipCount", 0, "message", message == null ? "" : message));
    }

    private List<Map<String, Object>> trimVisualizationNodes(
            List<Map<String, Object>> seedNodes,
            Map<String, Map<String, Object>> nodeMap,
            int nodeLimit) {
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Map<String, Object> node : seedNodes) {
            String id = String.valueOf(node.get("id"));
            if (seen.add(id)) {
                result.add(node);
            }
            if (result.size() >= nodeLimit) {
                return result;
            }
        }
        for (Map<String, Object> node : nodeMap.values()) {
            String id = String.valueOf(node.get("id"));
            if (seen.add(id)) {
                result.add(node);
            }
            if (result.size() >= nodeLimit) {
                return result;
            }
        }
        return result;
    }

    private void putVisualizationNode(Map<String, Map<String, Object>> nodeMap, Record record, String idField, String prefix) {
        String id = String.valueOf(record.get(idField).asLong());
        nodeMap.putIfAbsent(id, orderedMap(
                "id", id,
                "name", record.get(prefix + "Name").asString(""),
                "type", record.get(prefix + "Type").asString("Entity"),
                "description", record.get(prefix + "Description").asString(""),
                "sourceName", record.get(prefix + "SourceName").asString(""),
                "sourceUrl", record.get(prefix + "SourceUrl").asString(""),
                "visibility", record.get(prefix + "Visibility").asString("PUBLIC"),
                "degree", 0L));
    }

    private Map<String, Object> recordToSearchResult(Record record) {
        return orderedMap(
                "id", String.valueOf(record.get("id").asLong()),
                "name", record.get("name").asString(""),
                "type", record.get("type").asString("Entity"),
                "description", record.get("description").asString(""),
                "sourceName", record.get("sourceName").asString(""),
                "sourceUrl", record.get("sourceUrl").asString(""),
                "visibility", record.get("visibility").asString("PUBLIC"),
                "relationships", record.get("relationships").asList(value -> value.asMap()));
    }

    private Session session() {
        if (database.isBlank()) {
            return driver.session();
        }
        return driver.session(SessionConfig.forDatabase(database));
    }

    private String safeLabel(String value) {
        String cleaned = value == null ? "Entity" : value.replaceAll("[^A-Za-z0-9_]", "");
        return cleaned.isBlank() ? "Entity" : cleaned;
    }

    private String safeRelationship(String value) {
        String cleaned = value == null ? "RELATED_TO" : value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "");
        return cleaned.isBlank() ? "RELATED_TO" : cleaned;
    }

    private String documentKey(Long documentId, String documentTitle) {
        if (documentId != null) {
            return "document:" + documentId;
        }
        return "document:title:" + keyPart(documentTitle);
    }

    private String nodeKey(String documentKey, String documentTitle, String type, String name) {
        if ("Document".equals(type) && keyPart(documentTitle).equals(keyPart(name))) {
            return documentKey;
        }
        return documentKey + ":" + keyPart(type) + ":" + keyPart(name);
    }

    private String keyPart(String value) {
        String cleaned = value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return cleaned.isBlank() ? "unknown" : cleaned;
    }

    private Map<String, Object> orderedMap(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }

    private Map<String, Object> params(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }

    @PreDestroy
    public void close() {
        driver.close();
    }

    public record Triplet(
            String head,
            String headType,
            String relation,
            String relationLabel,
            String tail,
            String tailType,
            String headDescription,
            String tailDescription) {}

    public record GraphWriteResult(int nodesCreated, int relationshipsCreated) {}
}
