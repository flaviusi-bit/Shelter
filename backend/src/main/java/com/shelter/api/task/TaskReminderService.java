package com.shelter.api.task;

import com.shelter.api.animal.Animal;
import com.shelter.api.audit.AuditLogService;
import com.shelter.api.medical.Deworming;
import com.shelter.api.medical.DewormingRepository;
import com.shelter.api.medical.Vaccination;
import com.shelter.api.medical.VaccinationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DataIntegrityViolationException;
import java.time.ZoneId;

@Service
public class TaskReminderService {
    private static final int MEDICAL_PAGE_SIZE = 200;
    private final TaskRepository tasks;
    private final VaccinationRepository vaccinations;
    private final DewormingRepository dewormings;
    private final ZoneId zone;
    private final AuditLogService audit;

    public TaskReminderService(
            TaskRepository tasks,
            VaccinationRepository vaccinations,
            DewormingRepository dewormings,
            @Value("${shelter.timezone:Europe/Bucharest}") String timezone,
            AuditLogService audit) {
        this.tasks = tasks;
        this.vaccinations = vaccinations;
        this.dewormings = dewormings;
        this.zone = ZoneId.of(timezone);
        this.audit = audit;
    }

    @Scheduled(fixedDelayString = "${shelter.medical-reminder-sync-ms:3600000}")
    public void scheduledSync() {
        int created = syncAll();
        if (created > 0) audit.record("SYSTEM", "AUTO_SYNC_MEDICAL_REMINDERS", "TASK", null, "created=" + created);
    }

    public int syncAll() {
        int created = 0;
        int pageNumber = 0;
        Page<Vaccination> vaccinationPage;
        do {
            vaccinationPage = vaccinations.findAll(PageRequest.of(pageNumber, MEDICAL_PAGE_SIZE, Sort.by(Sort.Direction.ASC, "id")));
            java.util.Set<String> existingKeys = new java.util.HashSet<>(
                tasks.findBySourceKeyIn(vaccinationPage.getContent().stream()
                    .map(v -> "VACCINATION_DUE:" + v.getId()).toList())
                    .stream().map(Task::getSourceKey).toList());
            for (Vaccination v : vaccinationPage) {
                String key = "VACCINATION_DUE:" + v.getId();
                if (v.getNextDueDate() != null && !existingKeys.contains(key)) {
                    if (syncVaccination(v)) created++;
                }
            }
            pageNumber++;
        } while (vaccinationPage.hasNext());

        pageNumber = 0;
        Page<Deworming> dewormingPage;
        do {
            dewormingPage = dewormings.findAll(PageRequest.of(pageNumber, MEDICAL_PAGE_SIZE, Sort.by(Sort.Direction.ASC, "id")));
            java.util.Set<String> existingKeys = new java.util.HashSet<>(
                tasks.findBySourceKeyIn(dewormingPage.getContent().stream()
                    .map(d -> "DEWORMING_DUE:" + d.getId()).toList())
                    .stream().map(Task::getSourceKey).toList());
            for (Deworming d : dewormingPage) {
                String key = "DEWORMING_DUE:" + d.getId();
                if (d.getNextDueDate() != null && !existingKeys.contains(key)) {
                    if (syncDeworming(d)) created++;
                }
            }
            pageNumber++;
        } while (dewormingPage.hasNext());
        return created;
    }

    public boolean syncVaccination(Vaccination v) {
        return syncOrUpdate("VACCINATION_DUE:" + v.getId(), v.getAnimal(), "VACCINATION_DUE",
            "Vaccination due: " + v.getVaccineName(), v.getNextDueDate(), v.getNotes());
    }

    public boolean syncDeworming(Deworming d) {
        return syncOrUpdate("DEWORMING_DUE:" + d.getId(), d.getAnimal(), "DEWORMING_DUE",
            "Deworming due: " + d.getProductName(), d.getNextDueDate(), d.getNotes());
    }

    public void removeVaccinationReminder(Vaccination v) {
        removeReminder("VACCINATION_DUE:" + v.getId());
    }

    public void removeDewormingReminder(Deworming d) {
        removeReminder("DEWORMING_DUE:" + d.getId());
    }

    private boolean syncOrUpdate(String key, Animal animal, String type, String title, LocalDate date, String notes) {
        var existing = tasks.findBySourceKey(key);
        if (date == null) {
            existing.ifPresent(tasks::delete);
            return false;
        }
        if (existing.isPresent()) {
            Task task = existing.get();
            var dueAt = date.atStartOfDay(zone).plusHours(9).toOffsetDateTime();
            boolean dueDateChanged = task.getDueAt() == null || !task.getDueAt().isEqual(dueAt);
            task.setAnimal(animal);
            task.setTaskType(type);
            task.setTitle(title);
            task.setDueAt(dueAt);
            task.setNotes(notes);
            if ("OPEN".equals(task.getStatus()) || dueDateChanged) {
                if (dueDateChanged && !"OPEN".equals(task.getStatus())) {
                    task.setStatus("OPEN");
                    task.setCompletedAt(null);
                    task.setCompletedBy(null);
                }
                tasks.save(task);
            }
            return false;
        }
        return createIfMissing(key, animal, type, title, date, notes);
    }

    private void removeReminder(String key) {
        tasks.findBySourceKey(key).ifPresent(tasks::delete);
    }

    private boolean createIfMissing(String key, Animal animal, String type, String title, LocalDate date, String notes) {
        if (tasks.findBySourceKey(key).isPresent()) return false;
        Task t = new Task();
        t.setAnimal(animal);
        t.setTaskType(type);
        t.setTitle(title);
        t.setDueAt(date.atStartOfDay(zone).plusHours(9).toOffsetDateTime());
        t.setPriority("NORMAL");
        t.setNotes(notes);
        t.setCreatedBy("SYSTEM");
        t.setSourceKey(key);
        t.setStatus("OPEN");
        try {
            tasks.save(t);
            return true;
        } catch (DataIntegrityViolationException e) {
            // A concurrent sync may have created the same source key; the DB unique constraint wins.
            if (tasks.findBySourceKey(key).isEmpty()) throw e;
            return false;
        }
    }
}
