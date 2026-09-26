package com.shelter.api.task;

import com.shelter.api.animal.AnimalRepository;
import com.shelter.api.audit.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TaskControllerTest {
    private TaskRepository tasks;
    private AnimalRepository animals;
    private TaskReminderService reminders;
    private AuditLogService audit;
    private TaskController controller;
    private UUID taskId;
    private Task task;

    @BeforeEach
    void setUp() {
        tasks = mock(TaskRepository.class);
        animals = mock(AnimalRepository.class);
        reminders = mock(TaskReminderService.class);
        audit = mock(AuditLogService.class);
        controller = new TaskController(tasks, animals, reminders, audit);

        taskId = UUID.randomUUID();
        task = new Task();
        task.setTaskType("VACCINATION_DUE");
        task.setTitle("Vaccination due");
        task.setStatus("OPEN");
        when(tasks.findById(taskId)).thenReturn(Optional.of(task));
        when(tasks.save(any(Task.class))).thenAnswer(i -> i.getArgument(0));
    }



    @Test
    void createTaskSetsServerControlledFieldsAndAudits() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("coordinator");

        TaskController.TaskRequest request = new TaskController.TaskRequest(
            "FOLLOW_UP", "Call adopter", java.time.OffsetDateTime.parse("2026-09-27T10:00:00Z"),
            null, "volunteer", "Call tomorrow", null);

        Task result = controller.create(request, authentication);

        assertEquals("FOLLOW_UP", result.getTaskType());
        assertEquals("Call adopter", result.getTitle());
        assertEquals("2026-09-27T10:00Z", result.getDueAt().toString());
        assertEquals("NORMAL", result.getPriority());
        assertEquals("volunteer", result.getAssignedTo());
        assertEquals("Call tomorrow", result.getNotes());
        assertEquals("coordinator", result.getCreatedBy());
        assertEquals("OPEN", result.getStatus());
        assertEquals(null, result.getCompletedAt());
        assertEquals(null, result.getCompletedBy());
        assertEquals(null, result.getSourceKey());
        verify(tasks).save(result);
        verify(audit).record("coordinator", "CREATE_TASK", "TASK", null, "Call adopter");
    }

    @Test
    void createTaskResolvesAnimalAndRejectsUnknownAnimal() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("veterinarian");

        UUID animalId = UUID.randomUUID();
        com.shelter.api.animal.Animal animal = new com.shelter.api.animal.Animal();
        animal.setName("Misha");
        when(animals.findById(animalId)).thenReturn(Optional.of(animal));

        TaskController.TaskRequest request = new TaskController.TaskRequest(
            "CHECKUP", "Medical check", java.time.OffsetDateTime.parse("2026-09-28T12:00:00Z"),
            "HIGH", null, null, animalId);

        Task result = controller.create(request, authentication);

        assertEquals(animal, result.getAnimal());
        assertEquals("HIGH", result.getPriority());
        verify(tasks).save(result);

        UUID missingAnimalId = UUID.randomUUID();
        when(animals.findById(missingAnimalId)).thenReturn(Optional.empty());
        TaskController.TaskRequest missingAnimalRequest = new TaskController.TaskRequest(
            "CHECKUP", "Missing animal", java.time.OffsetDateTime.parse("2026-09-28T12:00:00Z"),
            "HIGH", null, null, missingAnimalId);

        org.springframework.web.server.ResponseStatusException ex =
            assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.create(missingAnimalRequest, authentication));

        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(tasks, times(1)).save(any(Task.class));
    }

    @Test
    void openTaskCanBeCompleted() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("volunteer");

        var result = controller.complete(taskId, authentication);

        assertEquals("COMPLETED", result.getStatus());
        verify(tasks).save(task);
        verify(audit).record("volunteer", "COMPLETE_TASK", "TASK", null, "Vaccination due");
    }

    @Test
    void completedTaskCannotBeCompletedAgain() {
        task.setStatus("COMPLETED");
        Authentication authentication = mock(Authentication.class);

        assertThrows(IllegalStateException.class, () -> controller.complete(taskId, authentication));
        verify(tasks, never()).save(any(Task.class));
        verify(audit, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void skippedTaskCannotBeSkippedAgain() {
        task.setStatus("SKIPPED");
        Authentication authentication = mock(Authentication.class);

        assertThrows(IllegalStateException.class, () -> controller.skip(taskId, authentication));
        verify(tasks, never()).save(any(Task.class));
        verify(audit, never()).record(any(), any(), any(), any(), any());
    }
}
