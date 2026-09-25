ALTER TABLE tasks ADD COLUMN source_key VARCHAR(180);
CREATE UNIQUE INDEX uq_tasks_source_key ON tasks(source_key) WHERE source_key IS NOT NULL;
CREATE INDEX idx_tasks_source_key ON tasks(source_key);
