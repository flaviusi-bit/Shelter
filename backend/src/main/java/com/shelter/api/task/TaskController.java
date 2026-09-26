package com.shelter.api.task;

import com.shelter.api.animal.AnimalRepository;
import com.shelter.api.audit.AuditLogService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskRepository tasks;
    private final AnimalRepository animals;
    private final TaskReminderService reminders;
    private final AuditLogService audit;

    public TaskController(TaskRepository tasks, AnimalRepository animals, TaskReminderService reminders, AuditLogService audit){this.tasks=tasks;this.animals=animals;this.reminders=reminders;this.audit=audit;}

    @GetMapping
    public List<Task> list(@RequestParam(defaultValue="OPEN") String status){
        return tasks.findByStatusOrderByDueAtAsc(status);
    }

    @PostMapping
    public Task create(@RequestBody Task input, Authentication auth){
        if(input.getAnimal()!=null && input.getAnimal().getId()!=null){
            input.setAnimal(animals.findById(input.getAnimal().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Animal not found")));
        } else input.setAnimal(null);
        input.setCreatedBy(auth.getName());
        input.setStatus("OPEN");
        input.setCompletedAt(null);
        input.setCompletedBy(null);
        input.setSourceKey(null);
        var saved=tasks.save(input);
        audit.record(auth.getName(),"CREATE_TASK","TASK",saved.getId(),saved.getTitle());
        return saved;
    }

    @PostMapping("/sync-medical-reminders")
    public java.util.Map<String,Integer> syncMedicalReminders(Authentication auth){ int created=reminders.syncAll(); audit.record(auth.getName(),"SYNC_MEDICAL_REMINDERS","TASK",null,"created="+created); return java.util.Map.of("created", created); }

    @PostMapping("/{id}/complete")
    public Task complete(@PathVariable UUID id, Authentication auth){
        Task task=tasks.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Task not found"));
        task.setStatus("COMPLETED");
        task.setCompletedAt(OffsetDateTime.now());
        task.setCompletedBy(auth.getName());
        var saved=tasks.save(task);
        audit.record(auth.getName(),"COMPLETE_TASK","TASK",saved.getId(),saved.getTitle());
        return saved;
    }

    @PostMapping("/{id}/skip")
    public Task skip(@PathVariable UUID id, Authentication auth){
        Task task=tasks.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Task not found"));
        task.setStatus("SKIPPED");
        task.setCompletedAt(OffsetDateTime.now());
        task.setCompletedBy(auth.getName());
        var saved=tasks.save(task);
        audit.record(auth.getName(),"SKIP_TASK","TASK",saved.getId(),saved.getTitle());
        return saved;
    }
}
