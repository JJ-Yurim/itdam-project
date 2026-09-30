ALTER TABLE ai_jobs ADD COLUMN correlation_id VARCHAR(36);
ALTER TABLE ai_jobs ADD COLUMN model_name VARCHAR(150);
ALTER TABLE ai_jobs ADD COLUMN prompt_version VARCHAR(100);

UPDATE ai_jobs
SET correlation_id = gen_random_uuid()::text
WHERE correlation_id IS NULL;

ALTER TABLE ai_jobs ALTER COLUMN correlation_id SET NOT NULL;
ALTER TABLE ai_jobs ADD CONSTRAINT uk_ai_jobs_correlation_id UNIQUE (correlation_id);
CREATE INDEX idx_ai_jobs_pending_dispatch
    ON ai_jobs (created_at)
    WHERE status = 'PENDING' AND request_dispatched = FALSE;
