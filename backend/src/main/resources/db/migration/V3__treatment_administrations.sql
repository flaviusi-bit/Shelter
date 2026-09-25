CREATE TABLE treatment_administrations (
    id UUID PRIMARY KEY,
    treatment_id UUID NOT NULL REFERENCES treatments(id) ON DELETE CASCADE,
    scheduled_at TIMESTAMPTZ NOT NULL,
    administered_at TIMESTAMPTZ,
    administered_by VARCHAR(120),
    status VARCHAR(30) NOT NULL DEFAULT 'SCHEDULED',
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_treatment_administration_status
        CHECK (status IN ('SCHEDULED','ADMINISTERED','MISSED','SKIPPED'))
);

CREATE INDEX idx_treatment_admin_treatment ON treatment_administrations(treatment_id);
CREATE INDEX idx_treatment_admin_schedule ON treatment_administrations(scheduled_at);
CREATE INDEX idx_treatment_admin_status ON treatment_administrations(status);
