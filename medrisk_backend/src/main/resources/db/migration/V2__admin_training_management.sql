CREATE TABLE training_datasets (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(120) NOT NULL,
  disease_type VARCHAR(40) NOT NULL,
  description TEXT,
  file_name VARCHAR(255) NOT NULL,
  file_path VARCHAR(500) NOT NULL,
  file_type VARCHAR(20) NOT NULL,
  status VARCHAR(30) NOT NULL,
  sample_count INT,
  feature_columns TEXT,
  validation_message TEXT,
  uploaded_by BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_training_dataset_user FOREIGN KEY (uploaded_by) REFERENCES users(id)
);

CREATE TABLE model_training_jobs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  dataset_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  disease_type VARCHAR(40) NOT NULL,
  model_name VARCHAR(120) NOT NULL,
  train_status VARCHAR(40) NOT NULL,
  progress INT NOT NULL,
  current_loss DOUBLE,
  train_epoch INT NOT NULL,
  learning_rate DOUBLE NOT NULL,
  test_size DOUBLE NOT NULL,
  model_version VARCHAR(160),
  model_path VARCHAR(500),
  history_path VARCHAR(500),
  metadata_path VARCHAR(500),
  metrics_json JSON,
  message TEXT,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_training_job_dataset FOREIGN KEY (dataset_id) REFERENCES training_datasets(id),
  CONSTRAINT fk_training_job_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE model_evaluations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  model_version_id BIGINT NOT NULL,
  dataset_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  metrics_json JSON NOT NULL,
  predictions_json JSON,
  created_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_evaluation_model FOREIGN KEY (model_version_id) REFERENCES model_versions(id),
  CONSTRAINT fk_evaluation_dataset FOREIGN KEY (dataset_id) REFERENCES training_datasets(id),
  CONSTRAINT fk_evaluation_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE model_feedback (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  model_version_id BIGINT,
  evaluation_id BIGINT,
  user_id BIGINT NOT NULL,
  problem_type VARCHAR(80) NOT NULL,
  priority VARCHAR(30) NOT NULL,
  status VARCHAR(30) NOT NULL,
  content TEXT NOT NULL,
  metrics_snapshot_json JSON,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_feedback_model FOREIGN KEY (model_version_id) REFERENCES model_versions(id),
  CONSTRAINT fk_feedback_evaluation FOREIGN KEY (evaluation_id) REFERENCES model_evaluations(id),
  CONSTRAINT fk_feedback_user FOREIGN KEY (user_id) REFERENCES users(id)
);
