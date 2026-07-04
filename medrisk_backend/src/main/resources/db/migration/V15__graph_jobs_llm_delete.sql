ALTER TABLE llm_model_profiles ADD COLUMN deleted_at TIMESTAMP NULL;
ALTER TABLE llm_model_profiles ADD COLUMN deleted_by BIGINT NULL;

CREATE INDEX idx_llm_profiles_deleted ON llm_model_profiles(deleted_at);

ALTER TABLE knowledge_graph_jobs ADD COLUMN processed_documents INT NOT NULL DEFAULT 0;
ALTER TABLE knowledge_graph_jobs ADD COLUMN total_documents INT NOT NULL DEFAULT 0;
ALTER TABLE knowledge_graph_jobs ADD COLUMN failed_documents INT NOT NULL DEFAULT 0;

CREATE INDEX idx_knowledge_graph_jobs_status ON knowledge_graph_jobs(status);
