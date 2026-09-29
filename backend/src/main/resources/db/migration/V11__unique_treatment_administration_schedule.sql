-- Prevent duplicate scheduled administrations for the same treatment/time,
-- including races between manual and scheduled generation.
ALTER TABLE treatment_administrations
    ADD CONSTRAINT uq_treatment_administration_schedule UNIQUE (treatment_id, scheduled_at);
