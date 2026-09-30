-- Restore the treatment/time uniqueness invariant for fresh databases and
-- remain safe when an older deployment already applied the former V11 constraint.
CREATE UNIQUE INDEX IF NOT EXISTS uq_treatment_administration_schedule
    ON treatment_administrations (treatment_id, scheduled_at);
