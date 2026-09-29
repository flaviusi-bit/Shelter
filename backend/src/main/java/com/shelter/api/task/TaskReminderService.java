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
            vaccinationPage = vaccinations.findAll(PageRequest.of(pageNumber, MEDICAL_PAGE_SIZE));
            for (Vaccination v : vaccinationPage) {
                if (v.getNextDueDate() != null && tasks.findBySourceKey("VACCINATION_DUE:" + v.getId()).isEmpty()) {
                    syncVaccination(v);
                    created++;
                }
            }
            pageNumber++;
        } while (vaccinationPage.hasNext());

        pageNumber = 0;
        Page<Deworming> dewormingPage;
        do {
            dewormingPage = dewormings.findAll(PageRequest.of(pageNumber, MEDICAL_PAGE_SIZE));
            for (Deworming d : dewormingPage) {
                if (d.getNextDueDate() != null && tasks.findBySourceKey("DEWORMING_DUE:" + d.getId()).isEmpty()) {
                    syncDeworming(d);
                    created++;
                }
            }
            pageNumber++;
        } while (dewormingPage.hasNext());
        return created;
    }

    public void syncVaccination(Vaccination v) {
        if (v.getNextDueDate() == null) return;
        createIfMissing("VACCINATION_DUE:" + v.getId(), v.getAnimal(), "VACCINATION_DUE",
            "Vaccination due: " + v.getVaccineName(), v.getNextDueDate(), v.getNotes());
    }

    public void syncDeworming(Deworming d) {
        if (d.getNextDueDate() == null) return;
        createIfMissing("DEWORMING_DUE:" + d.getId(), d.getAnimal(), "DEWORMING_DUE",
            "Deworming due: " + d.getProductName(), d.getNextDueDate(), d.getNotes());
    }

    private void createIfMissing(String key, Animal animal, String type, String title, LocalDate date, String notes) {
        if (tasks.findBySourceKey(key).isPresent()) return;
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
        tasks.save(t);
    }
}
