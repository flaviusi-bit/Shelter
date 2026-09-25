package com.shelter.api.task;

import com.shelter.api.animal.Animal;
import com.shelter.api.medical.Deworming;
import com.shelter.api.medical.DewormingRepository;
import com.shelter.api.medical.VaccinationRepository;
import com.shelter.api.medical.Vaccination;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

@Service
public class TaskReminderService {
    private final TaskRepository tasks;
    private final VaccinationRepository vaccinations;
    private final DewormingRepository dewormings;
    private final ZoneId zone;

    public TaskReminderService(TaskRepository tasks) {
        this.tasks = tasks;
        this.zone = ZoneId.of(System.getenv().getOrDefault("SHELTER_TIMEZONE", "Europe/Bucharest"));
    }

    public int syncAll() {\n        int created = 0;\n        for (Vaccination v : vaccinations.findAll()) {\n            if (v.getNextDueDate() != null && tasks.findBySourceKey("VACCINATION_DUE:"+v.getId()).isEmpty()) { syncVaccination(v); created++; }\n        }\n        for (Deworming d : dewormings.findAll()) {\n            if (d.getNextDueDate() != null && tasks.findBySourceKey("DEWORMING_DUE:"+d.getId()).isEmpty()) { syncDeworming(d); created++; }\n        }\n        return created;\n    }\n\n    public void syncVaccination(Vaccination v) {
        if (v.getNextDueDate() == null) return;
        Animal animal=v.getAnimal();
        String key="VACCINATION_DUE:"+v.getId();
        createIfMissing(key, animal, "VACCINATION_DUE",
            "Vaccination due: "+v.getVaccineName(), v.getNextDueDate(), v.getNotes());
    }

    public void syncDeworming(Deworming d) {
        if (d.getNextDueDate() == null) return;
        Animal animal=d.getAnimal();
        String key="DEWORMING_DUE:"+d.getId();
        createIfMissing(key, animal, "DEWORMING_DUE",
            "Deworming due: "+d.getProductName(), d.getNextDueDate(), d.getNotes());
    }

    private void createIfMissing(String key, Animal animal, String type, String title, LocalDate date, String notes) {
        if (tasks.findBySourceKey(key).isPresent()) return;
        Task t=new Task();
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
