CREATE TABLE llm_model_profiles (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  display_name VARCHAR(120) NOT NULL,
  provider VARCHAR(60) NOT NULL,
  base_url VARCHAR(500) NOT NULL,
  model_name VARCHAR(160) NOT NULL,
  api_key_cipher MEDIUMTEXT,
  reasoning_supported BOOLEAN NOT NULL DEFAULT FALSE,
  reasoning_protocol VARCHAR(40) NOT NULL DEFAULT 'none',
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  default_profile BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_llm_profiles_enabled ON llm_model_profiles(enabled);
CREATE INDEX idx_llm_profiles_default ON llm_model_profiles(default_profile);

ALTER TABLE qa_history ADD COLUMN model_profile_id BIGINT;
ALTER TABLE qa_history ADD COLUMN reasoning_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE qa_history ADD COLUMN reasoning_content MEDIUMTEXT;

CREATE INDEX idx_qa_history_model_profile ON qa_history(model_profile_id);
