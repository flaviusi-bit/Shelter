package com.shelter.api.treatment;

import com.shelter.api.animal.AnimalRepository;
import com.shelter.api.audit.AuditLogService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/animals/{animalId}/treatments/{treatmentId}/administrations")
public class TreatmentAdministrationController {
    private final TreatmentAdministrationRepository administrations;
    private final TreatmentRepository treatments;
    private final AnimalRepository animals;
    private final ZoneId zone;
    private final AuditLogService auditLog;

    public TreatmentAdministrationController(
            TreatmentAdministrationRepository administrations,
            TreatmentRepository treatments,
            AnimalRepository animals,
            AuditLogService auditLog,
            @Value("${shelter.timezone:Europe/Bucharest}") String timezone) {
        this.administrations = administrations;
        this.treatments = treatments;
        this.animals = animals;
        this.auditLog = auditLog;
        this.zone = ZoneId.of(timezone);
    }

    @GetMapping
    public List<TreatmentAdministration> list(
            @PathVariable UUID animalId,
            @PathVariable UUID treatmentId) {
        ensureTreatment(animalId, treatmentId);
        return administrations.findByTreatmentIdOrderByScheduledAtAsc(treatmentId);
    }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public List<TreatmentAdministration> generate(
            @PathVariable UUID animalId,
            @PathVariable UUID treatmentId,
            @RequestParam(defaultValue = "14") int days,
            Authentication authentication) {
        Treatment treatment = ensureTreatment(animalId, treatmentId);
        if (days < 1 || days > 90) {
            throw new IllegalArgumentException("days must be between 1 and 90");
        }

        Duration interval = parseFrequency(treatment.getFrequency());
        LocalDate end = treatment.getEndDate() != null
                ? treatment.getEndDate()
                : treatment.getStartDate().plusDays(days - 1);

        OffsetDateTime cursor = treatment.getStartDate().atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime limit = end.plusDays(1).atStartOfDay(zone).toOffsetDateTime();

        var existing = administrations.findByTreatmentIdOrderByScheduledAtAsc(treatmentId);
        var existingTimes = existing.stream()
                .map(TreatmentAdministration::getScheduledAt)
                .collect(java.util.stream.Collectors.toSet());

        java.util.ArrayList<TreatmentAdministration> created = new java.util.ArrayList<>();
        while (cursor.isBefore(limit) && created.size() < 1000) {
            if (!existingTimes.contains(cursor)) {
                var a = new TreatmentAdministration();
                a.setTreatment(treatment);
                a.setScheduledAt(cursor);
                created.add(administrations.save(a));
            }
            cursor = cursor.plus(interval);
        }
        if (!created.isEmpty()) {
            auditLog.record(authentication.getName(), "GENERATE_TREATMENT_SCHEDULE", "TREATMENT", treatmentId, "created=" + created.size());
        }
        return created;
    }

    @Scheduled(fixedDelayString = "${shelter.treatment-schedule-sync-ms:900000}")
    public void generateActiveSchedules() {
        treatments.findByStatusIgnoreCase("ACTIVE").forEach(t -> generateForTreatment(t, 14));
    }

    private List<TreatmentAdministration> generateForTreatment(Treatment treatment, int days) {
        Duration interval = parseFrequency(treatment.getFrequency());
        LocalDate end = treatment.getEndDate() != null ? treatment.getEndDate() : treatment.getStartDate().plusDays(days - 1);
        OffsetDateTime cursor = treatment.getStartDate().atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime limit = end.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        var existingTimes = administrations.findByTreatmentIdOrderByScheduledAtAsc(treatment.getId()).stream()
                .map(TreatmentAdministration::getScheduledAt).collect(java.util.stream.Collectors.toSet());
        java.util.ArrayList<TreatmentAdministration> created = new java.util.ArrayList<>();
        while (cursor.isBefore(limit) && created.size() < 1000) {
            if (!existingTimes.contains(cursor)) {
                var a = new TreatmentAdministration(); a.setTreatment(treatment); a.setScheduledAt(cursor);
                created.add(administrations.save(a));
            }
            cursor = cursor.plus(interval);
        }
        if (!created.isEmpty()) {
            auditLog.record("SYSTEM", "AUTO_GENERATE_TREATMENT_SCHEDULE", "TREATMENT", treatment.getId(), "created=" + created.size());
        }
        return created;
    }

    @PostMapping("/{administrationId}/administer")
    public TreatmentAdministration administer(
            @PathVariable UUID animalId,
            @PathVariable UUID treatmentId,
            @PathVariable UUID administrationId,
            @RequestBody(required = false) ActionRequest request,
            Authentication authentication) {
        ensureTreatment(animalId, treatmentId);
        TreatmentAdministration a = administrations.findById(administrationId)
                .orElseThrow(() -> new IllegalArgumentException("Administration not found: " + administrationId));
        if (!a.getTreatment().getId().equals(treatmentId)) {
            throw new IllegalArgumentException("Administration does not belong to treatment");
        }
        if (!"SCHEDULED".equals(a.getStatus())) {
            throw new IllegalStateException("Only scheduled administrations can be administered");
        }
        a.setStatus("ADMINISTERED");
        a.setAdministeredAt(OffsetDateTime.now());
        a.setAdministeredBy(authentication.getName());
        if (request != null) {
            a.setNotes(request.notes());
        }
        TreatmentAdministration saved = administrations.save(a);
        auditLog.record(authentication.getName(), "ADMINISTER_TREATMENT", "TREATMENT_ADMINISTRATION", saved.getId(), "status=ADMINISTERED");
        return saved;
    }

    @PostMapping("/{administrationId}/status")
    public TreatmentAdministration setStatus(
            @PathVariable UUID animalId,
            @PathVariable UUID treatmentId,
            @PathVariable UUID administrationId,
            @RequestBody ActionRequest request,
            Authentication authentication) {
        ensureTreatment(animalId, treatmentId);
        TreatmentAdministration a = administrations.findById(administrationId)
                .orElseThrow(() -> new IllegalArgumentException("Administration not found: " + administrationId));
        if (!a.getTreatment().getId().equals(treatmentId)) {
            throw new IllegalArgumentException("Administration does not belong to treatment");
        }
        if (!"SCHEDULED".equals(a.getStatus())) {
            throw new IllegalStateException("Only scheduled administrations can be marked missed or skipped");
        }
        if (!List.of("MISSED", "SKIPPED").contains(request.status())) {
            throw new IllegalArgumentException("Invalid status");
        }
        a.setStatus(request.status());
        a.setNotes(request.notes());
        TreatmentAdministration saved = administrations.save(a);
        auditLog.record(authentication.getName(), "UPDATE_TREATMENT_ADMINISTRATION_STATUS", "TREATMENT_ADMINISTRATION", saved.getId(), "status=" + request.status());
        return saved;
    }

    private Treatment ensureTreatment(UUID animalId, UUID treatmentId) {
        animals.findById(animalId)
                .orElseThrow(() -> new IllegalArgumentException("Animal not found: " + animalId));
        return treatments.findById(treatmentId)
                .filter(t -> t.getAnimal().getId().equals(animalId))
                .orElseThrow(() -> new IllegalArgumentException("Treatment not found: " + treatmentId));
    }

    private Duration parseFrequency(String frequency) {
        String f = frequency.trim().toLowerCase();
        if (f.matches("once|single")) return Duration.ofDays(36500);
        if (f.matches("daily|once daily|q24h|every 24 hours?")) return Duration.ofHours(24);
        if (f.matches("twice daily|two times daily|q12h|every 12 hours?")) return Duration.ofHours(12);
        if (f.matches("three times daily|q8h|every 8 hours?")) return Duration.ofHours(8);
        if (f.matches("four times daily|q6h|every 6 hours?")) return Duration.ofHours(6);
        var m = java.util.regex.Pattern.compile("every\\s+([1-9]\\d*)\\s+hours?").matcher(f);
        if (m.matches()) {
            long hours = parsePositiveInterval(m.group(1), "hours");
            if (hours > 24L * 365L) throw new IllegalArgumentException("Frequency interval is too large");
            return Duration.ofHours(hours);
        }
        var d = java.util.regex.Pattern.compile("every\\s+([1-9]\\d*)\\s+days?").matcher(f);
        if (d.matches()) {
            long days = parsePositiveInterval(d.group(1), "days");
            if (days > 365L) throw new IllegalArgumentException("Frequency interval is too large");
            return Duration.ofDays(days);
        }
        throw new IllegalArgumentException("Unsupported frequency for automatic scheduling. Use daily, twice daily, q12h, q8h, q6h, or 'every N hours/days'.");
    }

    private long parsePositiveInterval(String value, String unit) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid " + unit + " interval");
        }
    }

    public record ActionRequest(String notes, String status) {}
}
