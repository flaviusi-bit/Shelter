package com.shelter.api.treatment;

import com.shelter.api.animal.AnimalRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/animals/{animalId}/treatments/{treatmentId}/administrations")
public class TreatmentAdministrationController {
    private final TreatmentAdministrationRepository administrations;
    private final TreatmentRepository treatments;
    private final AnimalRepository animals;

    public TreatmentAdministrationController(
            TreatmentAdministrationRepository administrations,
            TreatmentRepository treatments,
            AnimalRepository animals) {
        this.administrations = administrations;
        this.treatments = treatments;
        this.animals = animals;
    }

    @GetMapping
    public List<TreatmentAdministration> list(@PathVariable UUID animalId, @PathVariable UUID treatmentId) {
        ensureTreatment(animalId, treatmentId);
        return administrations.findByTreatmentIdOrderByScheduledAtAsc(treatmentId);
    }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public List<TreatmentAdministration> generate(
            @PathVariable UUID animalId,
            @PathVariable UUID treatmentId,
            @RequestParam(defaultValue = "14") int days) {

        Treatment treatment = ensureTreatment(animalId, treatmentId);
        if (days < 1 || days > 90) throw new IllegalArgumentException("days must be between 1 and 90");

        Duration interval = parseFrequency(treatment.getFrequency());
        LocalDate end = treatment.getEndDate() != null
                ? treatment.getEndDate()
                : treatment.getStartDate().plusDays(days - 1);

        OffsetDateTime cursor = treatment.getStartDate().atStartOfDay(ZoneOffset.UTC);
        OffsetDateTime limit = end.plusDays(1).atStartOfDay(ZoneOffset.UTC);

        var existing = administrations.findByTreatmentIdOrderByScheduledAtAsc(treatmentId);
        var existingTimes = existing.stream().map(TreatmentAdministration::getScheduledAt).collect(java.util.stream.Collectors.toSet());

        java.util.ArrayList<TreatmentAdministration> created = new java.util.ArrayList<>();
        while (cursor.isBefore(limit) && created.size() < 1000) {
            if (!existingTimes.contains(cursor)) {
                var a = new TreatmentAdministration();
                a.setTreatment(treatment);
                a.setScheduledAt(cursor);
                a.setStatus("SCHEDULED");
                created.add(administrations.save(a));
            }
            cursor = cursor.plus(interval);
        }
        return created;
    }

    @PostMapping("/{administrationId}/administer")
    public TreatmentAdministration administer(
            @PathVariable UUID animalId,
            @PathVariable UUID treatmentId,
            @PathVariable UUID administrationId,
            @RequestBody(required = false) ActionRequest request) {

        ensureTreatment(animalId, treatmentId);
        TreatmentAdministration a = administrations.findById(administrationId)
                .orElseThrow(() -> new IllegalArgumentException("Administration not found: " + administrationId));
        if (!a.getTreatment().getId().equals(treatmentId))
            throw new IllegalArgumentException("Administration does not belong to treatment");

        a.setStatus("ADMINISTERED");
        a.setAdministeredAt(OffsetDateTime.now());
        a.setAdministeredBy(request == null ? null : request.administeredBy());
        if (request != null) a.setNotes(request.notes());
        return administrations.save(a);
    }

    @PostMapping("/{administrationId}/status")
    public TreatmentAdministration setStatus(
            @PathVariable UUID animalId,
            @PathVariable UUID treatmentId,
            @PathVariable UUID administrationId,
            @RequestBody ActionRequest request) {

        ensureTreatment(animalId, treatmentId);
        TreatmentAdministration a = administrations.findById(administrationId)
                .orElseThrow(() -> new IllegalArgumentException("Administration not found: " + administrationId));
        if (!a.getTreatment().getId().equals(treatmentId))
            throw new IllegalArgumentException("Administration does not belong to treatment");

        if (!List.of("SCHEDULED", "MISSED", "SKIPPED").contains(request.status()))
            throw new IllegalArgumentException("Invalid status");
        a.setStatus(request.status());
        a.setNotes(request.notes());
        return administrations.save(a);
    }

    private Treatment ensureTreatment(UUID animalId, UUID treatmentId) {
        animals.findById(animalId).orElseThrow(() -> new IllegalArgumentException("Animal not found: " + animalId));
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
        var m = java.util.regex.Pattern.compile("every\\s+(\\d+)\\s+hours?").matcher(f);
        if (m.matches()) return Duration.ofHours(Long.parseLong(m.group(1)));
        var d = java.util.regex.Pattern.compile("every\\s+(\\d+)\\s+days?").matcher(f);
        if (d.matches()) return Duration.ofDays(Long.parseLong(d.group(1)));
        throw new IllegalArgumentException(
                "Unsupported frequency for automatic scheduling. Use daily, twice daily, q12h, q8h, q6h, or 'every N hours/days'.");
    }

    public record ActionRequest(String administeredBy, String notes, String status) {}
}
