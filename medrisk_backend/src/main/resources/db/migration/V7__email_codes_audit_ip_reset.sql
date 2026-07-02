CREATE TABLE email_verification_codes (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  email VARCHAR(128) NOT NULL,
  purpose VARCHAR(40) NOT NULL,
  code_hash VARCHAR(128) NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  consumed BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  consumed_at TIMESTAMP NULL
);

CREATE INDEX idx_email_verification_lookup ON email_verification_codes(email, purpose, consumed, created_at);

DELETE FROM audit_logs;
