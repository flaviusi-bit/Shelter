package com.shelter.api.task;

import com.shelter.api.animal.Animal;
import com.shelter.api.medical.Deworming;
import com.shelter.api.medical.DewormingRepository;
import com.shelter.api.medical.Vaccination;
import com.shelter.api.medical.VaccinationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class TaskReminderService {
    private final TaskRepository tasks;
    private final VaccinationRepository vaccinations;
    private final DewormingRepository dewormings;
    private final ZoneId zone;

    public TaskReminderService(
            TaskRepository tasks,
            VaccinationRepository vaccinations,
            DewormingRepository dewormings,
            @Value("${shelter.timezone:Europe/Bucharest}") String timezone) {
        this.tasks = tasks;
        this.vaccinations = vaccinations;
        this.dewormings = dewormings;
        this.zone = ZoneId.of(timezone);
    }

    public int syncAll() {
        int created = 0;
        for (Vaccination v : vaccinations.findAll()) {
            if (v.getNextDueDate() != null && tasks.findBySourceKey("VACCINATION_DUE:" + v.getId()).isEmpty()) {
                syncVaccination(v);
                created++;
            }
        }
        for (Deworming d : dewormings.findAll()) {
            if (d.getNextDueDate() != null && tasks.findBySourceKey("DEWORMING_DUE:" + d.getId()).isEmpty()) {
                syncDeworming(d);
                created++;
            }
        }
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
