package com.shelter.api.dashboard;

import com.shelter.api.treatment.TreatmentAdministration;
import com.shelter.api.treatment.TreatmentAdministrationRepository;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final TreatmentAdministrationRepository administrations;

    public DashboardController(TreatmentAdministrationRepository administrations) {
        this.administrations = administrations;
    }

    @GetMapping("/today")
    public Dashboard today() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime start = now.toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime end = start.plusDays(1);

        List<TreatmentAdministration> rows = administrations
            .findAll()
            .stream()
            .filter(a -> !a.getScheduledAt().isBefore(start) && a.getScheduledAt().isBefore(end))
            .sorted(Comparator.comparing(TreatmentAdministration::getScheduledAt))
            .toList();

        long overdue = rows.stream().filter(a -> "SCHEDULED".equals(a.getStatus()) && a.getScheduledAt().isBefore(now)).count();
        long administered = rows.stream().filter(a -> "ADMINISTERED".equals(a.getStatus())).count();
        long remaining = rows.stream().filter(a -> "SCHEDULED".equals(a.getStatus()) && !a.getScheduledAt().isBefore(now)).count();

        var items = rows.stream().map(a -> new DashboardItem(
            a.getId(),
            a.getTreatment().getAnimal().getId(),
            a.getTreatment().getAnimal().getName(),
            a.getTreatment().getMedication(),
            a.getTreatment().getDose(),
            a.getTreatment().getRoute(),
            a.getScheduledAt(),
            a.getAdministeredAt(),
            a.getAdministeredBy(),
            a.getStatus()
        )).toList();

        return new Dashboard(items, overdue, remaining, administered);
    }

    public record Dashboard(List<DashboardItem> items, long overdue, long remaining, long administered) {}
    public record DashboardItem(
        UUID id, UUID animalId, String animalName, String medication, String dose,
        String route, OffsetDateTime scheduledAt, OffsetDateTime administeredAt,
        String administeredBy, String status) {}
}
