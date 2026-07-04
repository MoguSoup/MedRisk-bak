CREATE INDEX idx_model_versions_created_at ON model_versions(created_at);
CREATE INDEX idx_model_training_jobs_created_at ON model_training_jobs(created_at);
CREATE INDEX idx_model_evaluations_created_at ON model_evaluations(created_at);
CREATE INDEX idx_model_feedback_created_at ON model_feedback(created_at);
