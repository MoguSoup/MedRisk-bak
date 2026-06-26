ALTER TABLE knowledge_documents ADD COLUMN source_name VARCHAR(120);
ALTER TABLE knowledge_documents ADD COLUMN source_url VARCHAR(500);
ALTER TABLE knowledge_documents ADD COLUMN source_license VARCHAR(120);
ALTER TABLE knowledge_documents ADD COLUMN source_record_id VARCHAR(160);
ALTER TABLE knowledge_documents ADD COLUMN retrieved_at TIMESTAMP;
ALTER TABLE knowledge_documents ADD COLUMN visibility VARCHAR(30) NOT NULL DEFAULT 'PUBLIC';

ALTER TABLE disease_info ADD COLUMN source_name VARCHAR(120);
ALTER TABLE disease_info ADD COLUMN source_url VARCHAR(500);
ALTER TABLE disease_info ADD COLUMN source_license VARCHAR(120);
ALTER TABLE disease_info ADD COLUMN source_record_id VARCHAR(160);
ALTER TABLE disease_info ADD COLUMN retrieved_at TIMESTAMP;
ALTER TABLE disease_info ADD COLUMN visibility VARCHAR(30) NOT NULL DEFAULT 'PUBLIC';
ALTER TABLE disease_info ADD COLUMN created_by BIGINT;
ALTER TABLE disease_info ADD COLUMN created_by_name VARCHAR(120);

ALTER TABLE medical_cases ADD COLUMN source_name VARCHAR(120);
ALTER TABLE medical_cases ADD COLUMN source_url VARCHAR(500);
ALTER TABLE medical_cases ADD COLUMN source_license VARCHAR(120);
ALTER TABLE medical_cases ADD COLUMN source_record_id VARCHAR(160);
ALTER TABLE medical_cases ADD COLUMN retrieved_at TIMESTAMP;
ALTER TABLE medical_cases ADD COLUMN visibility VARCHAR(30) NOT NULL DEFAULT 'PUBLIC';
ALTER TABLE medical_cases ADD COLUMN created_by BIGINT;
ALTER TABLE medical_cases ADD COLUMN created_by_name VARCHAR(120);
ALTER TABLE medical_cases ADD COLUMN synthetic_case BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE training_datasets ADD COLUMN source_name VARCHAR(120);
ALTER TABLE training_datasets ADD COLUMN source_url VARCHAR(500);
ALTER TABLE training_datasets ADD COLUMN source_license VARCHAR(120);
ALTER TABLE training_datasets ADD COLUMN source_record_id VARCHAR(160);
ALTER TABLE training_datasets ADD COLUMN retrieved_at TIMESTAMP;
ALTER TABLE training_datasets ADD COLUMN visibility VARCHAR(30) NOT NULL DEFAULT 'ADMIN_ONLY';

CREATE INDEX idx_knowledge_documents_visibility ON knowledge_documents(visibility);
CREATE INDEX idx_knowledge_documents_source_record ON knowledge_documents(source_record_id);
CREATE INDEX idx_disease_info_visibility ON disease_info(visibility);
CREATE INDEX idx_disease_info_source_record ON disease_info(source_record_id);
CREATE INDEX idx_medical_cases_visibility ON medical_cases(visibility);
CREATE INDEX idx_medical_cases_source_record ON medical_cases(source_record_id);
CREATE INDEX idx_training_datasets_source_record ON training_datasets(source_record_id);

CREATE TABLE IF NOT EXISTS data_seed_runs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  seed_key VARCHAR(120) NOT NULL,
  status VARCHAR(40) NOT NULL,
  disease_count INT NOT NULL DEFAULT 0,
  document_count INT NOT NULL DEFAULT 0,
  case_count INT NOT NULL DEFAULT 0,
  dataset_count INT NOT NULL DEFAULT 0,
  graph_node_count INT NOT NULL DEFAULT 0,
  graph_relationship_count INT NOT NULL DEFAULT 0,
  message TEXT,
  started_by BIGINT,
  started_at TIMESTAMP NOT NULL,
  finished_at TIMESTAMP
);

CREATE INDEX idx_data_seed_runs_seed_key ON data_seed_runs(seed_key);
CREATE INDEX idx_data_seed_runs_started_at ON data_seed_runs(started_at);
