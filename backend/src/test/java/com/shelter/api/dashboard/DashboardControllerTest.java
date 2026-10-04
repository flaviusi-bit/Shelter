package com.shelter.api.dashboard;

import com.shelter.api.animal.Animal;
import com.shelter.api.task.Task;
import com.shelter.api.task.TaskRepository;
import com.shelter.api.treatment.TreatmentAdministrationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardControllerTest {

    @Test
    void todayIncludesOpenTasksInTaskItems() {
        TreatmentAdministrationRepository administrations = mock(TreatmentAdministrationRepository.class);
        TaskRepository tasks = mock(TaskRepository.class);
        DashboardController controller = new DashboardController(administrations, tasks, "Europe/Bucharest");

        Animal animal = new Animal();
        animal.setName("Misha");

        Task task = new Task();
        task.setAnimal(animal);
        task.setTaskType("VET_VISIT");
        task.setTitle("Test reminder");
        task.setDueAt(OffsetDateTime.parse("2026-10-11T09:00:00+03:00"));
        task.setStatus("OPEN");
        task.setPriority("HIGH");
        task.setAssignedTo("coordinator");
        task.setNotes("test");

        when(tasks.findByStatusOrderByDueAtAscIdAsc(org.mockito.ArgumentMatchers.eq("OPEN"), any(Pageable.class)))
            .thenReturn(List.of(task));

        DashboardController.Dashboard dashboard = controller.today(mock(jakarta.servlet.http.HttpServletResponse.class));

        assertEquals(1, dashboard.taskItems().size());
        assertEquals(task.getId(), dashboard.taskItems().get(0).id());
        assertEquals("Misha", dashboard.taskItems().get(0).animalName());
        assertEquals("Test reminder", dashboard.taskItems().get(0).title());
        assertEquals("OPEN", dashboard.taskItems().get(0).status());
    }
}
