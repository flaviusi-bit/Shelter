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
