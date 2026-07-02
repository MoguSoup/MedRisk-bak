ALTER TABLE qa_history ADD COLUMN chat_mode VARCHAR(20) NOT NULL DEFAULT 'medical';
ALTER TABLE qa_history ADD COLUMN retrieval_used BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE qa_history ADD COLUMN retrieval_status VARCHAR(40) NOT NULL DEFAULT 'success';
ALTER TABLE qa_history ADD COLUMN generated_images_json MEDIUMTEXT;

UPDATE qa_history SET chat_mode = 'medical' WHERE chat_mode IS NULL OR chat_mode = '';
UPDATE qa_history SET retrieval_used = TRUE WHERE retrieval_used IS NULL;
UPDATE qa_history SET retrieval_status = 'success' WHERE retrieval_status IS NULL OR retrieval_status = '';
