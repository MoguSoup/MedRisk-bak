CREATE TABLE knowledge_documents (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(255) NOT NULL,
  original_file_name VARCHAR(255) NOT NULL,
  file_type VARCHAR(30) NOT NULL,
  file_size BIGINT NOT NULL,
  file_bucket VARCHAR(80) NOT NULL,
  file_object_key VARCHAR(500) NOT NULL,
  content MEDIUMTEXT,
  summary TEXT,
  graph_status VARCHAR(40) NOT NULL DEFAULT '未构建',
  uploaded_by BIGINT NOT NULL,
  user_name VARCHAR(80),
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_knowledge_document_user FOREIGN KEY (uploaded_by) REFERENCES users(id)
);

CREATE INDEX idx_knowledge_documents_uploaded_by ON knowledge_documents(uploaded_by);
CREATE INDEX idx_knowledge_documents_graph_status ON knowledge_documents(graph_status);
CREATE INDEX idx_knowledge_documents_created_at ON knowledge_documents(created_at);

CREATE TABLE knowledge_graph_jobs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_type VARCHAR(40) NOT NULL,
  status VARCHAR(40) NOT NULL,
  progress INT NOT NULL,
  message TEXT,
  nodes_created INT NOT NULL DEFAULT 0,
  relationships_created INT NOT NULL DEFAULT 0,
  document_id BIGINT,
  started_by BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_graph_job_document FOREIGN KEY (document_id) REFERENCES knowledge_documents(id),
  CONSTRAINT fk_graph_job_user FOREIGN KEY (started_by) REFERENCES users(id)
);

CREATE INDEX idx_graph_jobs_created_at ON knowledge_graph_jobs(created_at);
CREATE INDEX idx_graph_jobs_status ON knowledge_graph_jobs(status);

CREATE TABLE conversations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(255) NOT NULL,
  user_id BIGINT NOT NULL,
  user_name VARCHAR(80),
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_conversation_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_conversation_user_id ON conversations(user_id);
CREATE INDEX idx_conversation_updated_at ON conversations(updated_at);

CREATE TABLE qa_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  conversation_id BIGINT NOT NULL,
  question MEDIUMTEXT NOT NULL,
  answer MEDIUMTEXT,
  related_entities_json MEDIUMTEXT,
  graph_context_json MEDIUMTEXT,
  disease_info_matches_json MEDIUMTEXT,
  disease_case_matches_json MEDIUMTEXT,
  keywords_json TEXT,
  image_bucket VARCHAR(80),
  image_object_key VARCHAR(500),
  image_url VARCHAR(700),
  user_id BIGINT NOT NULL,
  user_name VARCHAR(80),
  created_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_qa_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
  CONSTRAINT fk_qa_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_qa_history_conversation_id ON qa_history(conversation_id);
CREATE INDEX idx_qa_history_user_id ON qa_history(user_id);
CREATE INDEX idx_qa_history_created_at ON qa_history(created_at);

CREATE TABLE disease_info (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  disease_code VARCHAR(50) NOT NULL,
  disease_name VARCHAR(100) NOT NULL,
  disease_name_en VARCHAR(100),
  disease_category VARCHAR(80),
  department VARCHAR(80),
  pathogen VARCHAR(120),
  symptoms TEXT,
  risk_factors TEXT,
  prevention_measures TEXT,
  treatment_plan TEXT,
  severity_level VARCHAR(50),
  is_infectious BOOLEAN NOT NULL DEFAULT FALSE,
  incubation_period VARCHAR(120),
  common_complications TEXT,
  prognosis VARCHAR(220),
  description MEDIUMTEXT,
  image_bucket VARCHAR(80),
  image_object_key VARCHAR(500),
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_disease_info_name ON disease_info(disease_name);
CREATE INDEX idx_disease_info_code ON disease_info(disease_code);
CREATE INDEX idx_disease_info_department ON disease_info(department);

CREATE TABLE medical_cases (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  disease_id BIGINT NOT NULL,
  case_title VARCHAR(255) NOT NULL,
  visit_date TIMESTAMP NULL,
  hospital VARCHAR(255),
  patient_age INT,
  patient_gender VARCHAR(20),
  affected_area VARCHAR(200),
  severity_level VARCHAR(50),
  chief_complaint TEXT,
  present_illness TEXT,
  past_history TEXT,
  physical_examination TEXT,
  lab_results TEXT,
  imaging_results TEXT,
  symptom_description TEXT,
  diagnosis TEXT,
  treatment_given TEXT,
  treatment_cost DOUBLE,
  treatment_outcome VARCHAR(120),
  followup_notes TEXT,
  images_json MEDIUMTEXT,
  data_source VARCHAR(255),
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_medical_case_disease FOREIGN KEY (disease_id) REFERENCES disease_info(id) ON DELETE CASCADE
);

CREATE INDEX idx_medical_cases_disease_id ON medical_cases(disease_id);
CREATE INDEX idx_medical_cases_hospital ON medical_cases(hospital);
CREATE INDEX idx_medical_cases_patient_age ON medical_cases(patient_age);
CREATE INDEX idx_medical_cases_severity ON medical_cases(severity_level);
