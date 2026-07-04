ALTER TABLE users ADD COLUMN current_session_id VARCHAR(64);

CREATE INDEX idx_users_current_session_id ON users(current_session_id);
