package com.shelter.api.treatment;

import com.shelter.api.audit.AuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class TreatmentAdministrationScheduleService {
    private final TreatmentAdministrationRepository administrations;
    private final AuditLogService auditLog;

    public TreatmentAdministrationScheduleService(
            TreatmentAdministrationRepository administrations,
            AuditLogService auditLog) {
        this.administrations = administrations;
        this.auditLog = auditLog;
    }

    @Transactional
    public TreatmentAdministration createAndAudit(
            Treatment treatment,
            OffsetDateTime scheduledAt,
            String actor) {
        var administration = new TreatmentAdministration();
        administration.setTreatment(treatment);
        administration.setScheduledAt(scheduledAt);
        var saved = administrations.save(administration);
        auditLog.record(actor, "GENERATE_TREATMENT_SCHEDULE", "TREATMENT", treatment.getId(),
                "created=1 scheduledAt=" + scheduledAt);
        return saved;
    }
}
