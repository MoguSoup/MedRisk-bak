UPDATE users
SET status = 'ACTIVE'
WHERE status IS NULL OR status = '';

UPDATE users
SET updated_at = created_at
WHERE updated_at IS NULL;
