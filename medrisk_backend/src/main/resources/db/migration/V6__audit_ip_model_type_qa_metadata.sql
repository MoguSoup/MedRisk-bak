ALTER TABLE audit_logs ADD COLUMN client_ip VARCHAR(45);

ALTER TABLE model_training_jobs ADD COLUMN model_type VARCHAR(30) NOT NULL DEFAULT 'xgboost';
ALTER TABLE model_versions ADD COLUMN model_type VARCHAR(30) NOT NULL DEFAULT 'xgboost';

ALTER TABLE qa_history ADD COLUMN used_model VARCHAR(120);
ALTER TABLE qa_history ADD COLUMN provider VARCHAR(80);
ALTER TABLE qa_history ADD COLUMN fallback_used BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE qa_history ADD COLUMN evidence_sources_json TEXT;

CREATE INDEX idx_audit_logs_client_ip ON audit_logs(client_ip);
CREATE INDEX idx_model_training_jobs_model_type ON model_training_jobs(model_type);
CREATE INDEX idx_model_versions_model_type ON model_versions(model_type);
