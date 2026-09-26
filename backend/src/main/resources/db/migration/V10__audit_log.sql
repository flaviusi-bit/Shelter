CREATE TABLE audit_log (
    id UUID PRIMARY KEY,
    actor VARCHAR(120) NOT NULL,
    action VARCHAR(80) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id UUID,
    occurred_at TIMESTAMPTZ NOT NULL,
    details TEXT
);

CREATE INDEX idx_audit_log_occurred_at ON audit_log(occurred_at DESC);
CREATE INDEX idx_audit_log_entity ON audit_log(entity_type, entity_id);
