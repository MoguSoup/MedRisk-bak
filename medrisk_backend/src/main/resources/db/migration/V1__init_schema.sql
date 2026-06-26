CREATE TABLE users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL UNIQUE,
  email VARCHAR(128) NOT NULL UNIQUE,
  password_hash VARCHAR(128) NOT NULL,
  role VARCHAR(24) NOT NULL,
  name VARCHAR(80) NOT NULL,
  phone VARCHAR(40),
  avatar_url VARCHAR(260),
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  last_login_at TIMESTAMP NULL
);

CREATE TABLE patient_profiles (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  name VARCHAR(80) NOT NULL,
  gender VARCHAR(16),
  age INT,
  height DOUBLE,
  weight DOUBLE,
  phone VARCHAR(40),
  medical_history JSON,
  created_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_patient_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE prediction_records (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  patient_name VARCHAR(80),
  disease_type VARCHAR(40) NOT NULL,
  disease_name VARCHAR(40) NOT NULL,
  input_json JSON NOT NULL,
  result_json JSON NOT NULL,
  risk_label VARCHAR(24) NOT NULL,
  risk_probability DOUBLE NOT NULL,
  confidence DOUBLE NOT NULL,
  model_version VARCHAR(120) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_prediction_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE report_records (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  prediction_id BIGINT NOT NULL,
  report_title VARCHAR(160) NOT NULL,
  report_html MEDIUMTEXT NOT NULL,
  report_pdf_path VARCHAR(260),
  generated_by BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_report_prediction FOREIGN KEY (prediction_id) REFERENCES prediction_records(id),
  CONSTRAINT fk_report_user FOREIGN KEY (generated_by) REFERENCES users(id)
);

CREATE TABLE model_versions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  disease_type VARCHAR(40) NOT NULL,
  disease_name VARCHAR(40) NOT NULL,
  model_name VARCHAR(80) NOT NULL,
  version VARCHAR(120) NOT NULL UNIQUE,
  metrics_json JSON NOT NULL,
  feature_schema_json JSON,
  model_path VARCHAR(260),
  active BOOLEAN NOT NULL,
  created_at TIMESTAMP NOT NULL
);

CREATE TABLE audit_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT,
  action VARCHAR(80) NOT NULL,
  resource_type VARCHAR(80) NOT NULL,
  resource_id VARCHAR(80),
  detail_json JSON,
  created_at TIMESTAMP NOT NULL
);
