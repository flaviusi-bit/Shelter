package com.shelter.api.treatment;

import com.shelter.api.animal.Animal;
import com.shelter.api.animal.AnimalRepository;
import com.shelter.api.audit.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TreatmentControllerTest {
    private TreatmentRepository treatments;
    private AnimalRepository animals;
    private AuditLogService audit;
    private TreatmentController controller;
    private Animal animal;
    private UUID animalId;

    @BeforeEach
    void setUp() {
        treatments = mock(TreatmentRepository.class);
        animals = mock(AnimalRepository.class);
        audit = mock(AuditLogService.class);
        controller = new TreatmentController(treatments, animals, audit);
        animalId = UUID.randomUUID();
        animal = new Animal();
        animal.setId(animalId);
        when(animals.findById(animalId)).thenReturn(Optional.of(animal));
        when(treatments.save(any(Treatment.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void rejectsEndDateBeforeStartDate() {
        var request = new TreatmentController.Request(
            "Amoxicillin", "10 mg", "ORAL", "once daily",
            LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 19),
            "ACTIVE", "instructions", "vet");
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () ->
            controller.create(animalId, request, mock(Authentication.class)));
        verify(treatments, never()).save(any());
    }

    @Test
    void createsTreatmentWithDefaultActiveStatus() {
        var auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("vet");
        var request = new TreatmentController.Request(
            "Amoxicillin", "10 mg", "ORAL", "once daily",
            LocalDate.of(2026, 9, 20), null, null, null, "vet");
        var result = controller.create(animalId, request, auth);
        assertEquals("ACTIVE", result.getStatus());
        verify(treatments).save(any(Treatment.class));
    }
}
