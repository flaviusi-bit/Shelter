package com.shelter.api.dashboard;

import com.shelter.api.task.Task;
import com.shelter.api.task.TaskDue;
import com.shelter.api.task.TaskRepository;
import com.shelter.api.treatment.TreatmentAdministration;
import com.shelter.api.treatment.TreatmentAdministrationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final TreatmentAdministrationRepository administrations;
    private final TaskRepository tasks;
    private final ZoneId zone;

    public DashboardController(
            TreatmentAdministrationRepository administrations,
            TaskRepository tasks,
            @Value("${shelter.timezone:Europe/Bucharest}") String timezone) {
        this.administrations = administrations;
        this.tasks = tasks;
        this.zone = ZoneId.of(timezone);
    }

    @GetMapping("/today")
    public Dashboard today() {
        ZonedDateTime zonedNow = ZonedDateTime.now(zone);
        OffsetDateTime now = zonedNow.toOffsetDateTime();
        OffsetDateTime start = zonedNow.toLocalDate().atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime end = start.plusDays(1);

        long overdue = administrations.countByStatusAndScheduledAtGreaterThanEqualAndScheduledAtLessThan("SCHEDULED", start, now);
        long administered = administrations.countByStatusAndScheduledAtGreaterThanEqualAndScheduledAtLessThan("ADMINISTERED", start, end);
        long remaining = administrations.countByStatusAndScheduledAtGreaterThanEqualAndScheduledAtLessThan("SCHEDULED", now, end);

        List<TreatmentAdministration> rows = administrations.findByScheduledAtGreaterThanEqualAndScheduledAtLessThanOrderByScheduledAtAsc(start, end, PageRequest.of(0, 500));

        var items = rows.stream().map(a -> new DashboardItem(
            a.getId(), a.getTreatment().getAnimal().getId(), a.getTreatment().getAnimal().getName(),
            a.getTreatment().getMedication(), a.getTreatment().getDose(), a.getTreatment().getRoute(),
            a.getScheduledAt(), a.getAdministeredAt(), a.getAdministeredBy(), a.getStatus()
        )).toList();

        long overdueTasks = tasks.countByStatusAndDueAtBefore("OPEN", now);
        long remainingTasks = tasks.countByStatusAndDueAtGreaterThanEqualAndDueAtBefore("OPEN", now, end);
        List<Task> openTasks = tasks.findByStatusOrderByDueAtAscIdAsc("OPEN", PageRequest.of(0, 50));

        var taskItems = openTasks.stream().map(t -> new TaskDue(
            t.getId(), t.getAnimal() == null ? null : t.getAnimal().getId(),
            t.getAnimal() == null ? null : t.getAnimal().getName(), t.getTaskType(), t.getTitle(),
            t.getDueAt(), t.getStatus(), t.getPriority(), t.getAssignedTo(), t.getNotes()
        )).toList();

        return new Dashboard(items, overdue, remaining, administered, overdueTasks, remainingTasks, taskItems);
    }

    public record Dashboard(List<DashboardItem> items, long overdue, long remaining, long administered,
                            long overdueTasks, long remainingTasks, List<TaskDue> taskItems) {}

    public record DashboardItem(UUID id, UUID animalId, String animalName, String medication, String dose,
                                String route, OffsetDateTime scheduledAt, OffsetDateTime administeredAt,
                                String administeredBy, String status) {}
}
