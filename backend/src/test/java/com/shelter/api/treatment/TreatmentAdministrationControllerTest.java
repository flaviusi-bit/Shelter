package com.shelter.api.treatment;

import com.shelter.api.animal.Animal;
import com.shelter.api.animal.AnimalRepository;
import com.shelter.api.audit.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.Authentication;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TreatmentAdministrationControllerTest {
    private TreatmentAdministrationRepository administrations;
    private TreatmentRepository treatments;
    private AnimalRepository animals;
    private TreatmentAdministrationController controller;
    private AuditLogService auditLog;
    private UUID animalId;
    private UUID treatmentId;
    private UUID administrationId;
    private Treatment treatment;
    private TreatmentAdministration administration;

    @BeforeEach
    void setUp() {
        administrations = mock(TreatmentAdministrationRepository.class);
        treatments = mock(TreatmentRepository.class);
        animals = mock(AnimalRepository.class);
        auditLog = mock(AuditLogService.class);
        controller = new TreatmentAdministrationController(administrations, treatments, animals, auditLog, "Europe/Bucharest");

        animalId = UUID.randomUUID();
        treatmentId = UUID.randomUUID();
        administrationId = UUID.randomUUID();

        Animal animal = mock(Animal.class);
        when(animal.getId()).thenReturn(animalId);

        treatment = mock(Treatment.class);
        when(treatment.getId()).thenReturn(treatmentId);
        when(treatment.getAnimal()).thenReturn(animal);
        when(treatments.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(animals.findById(animalId)).thenReturn(Optional.of(animal));

        administration = new TreatmentAdministration();
        administration.setTreatment(treatment);
        administration.setScheduledAt(OffsetDateTime.now());
        when(administrations.findById(administrationId)).thenReturn(Optional.of(administration));
        when(administrations.save(any(TreatmentAdministration.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void rejectsZeroFrequencyInterval() {
        when(treatment.getFrequency()).thenReturn("every 0 hours");
        when(treatment.getStartDate()).thenReturn(java.time.LocalDate.now());
        assertThrows(IllegalArgumentException.class, () ->
            controller.generate(animalId, treatmentId, 1, Mockito.mock(Authentication.class)));
    }

    @Test
    void rejectsNegativeFrequencyInterval() {
        when(treatment.getFrequency()).thenReturn("every -1 hours");
        when(treatment.getStartDate()).thenReturn(java.time.LocalDate.now());
        assertThrows(IllegalArgumentException.class, () ->
            controller.generate(animalId, treatmentId, 1, Mockito.mock(Authentication.class)));
    }

    @Test
    void rejectsExcessivelyLargeFrequencyInterval() {
        when(treatment.getFrequency()).thenReturn("every 366 days");
        when(treatment.getStartDate()).thenReturn(java.time.LocalDate.now());
        assertThrows(IllegalArgumentException.class, () ->
            controller.generate(animalId, treatmentId, 1, Mockito.mock(Authentication.class)));
    }

    @Test
    void cannotAdministerAlreadyFinalizedAdministration() {
        Authentication authentication = Mockito.mock(Authentication.class);
        when(authentication.getName()).thenReturn("vet");

        controller.administer(animalId, treatmentId, administrationId,
            new TreatmentAdministrationController.ActionRequest("given", null), authentication);

        assertThrows(IllegalStateException.class, () ->
            controller.administer(animalId, treatmentId, administrationId,
                new TreatmentAdministrationController.ActionRequest("again", null), authentication));
    }

    @Test
    void cannotMarkFinalizedAdministrationAsSkipped() {
        Authentication authentication = Mockito.mock(Authentication.class);
        when(authentication.getName()).thenReturn("vet");

        controller.administer(animalId, treatmentId, administrationId,
            new TreatmentAdministrationController.ActionRequest("given", null), authentication);

        assertThrows(IllegalStateException.class, () ->
            controller.setStatus(animalId, treatmentId, administrationId,
                new TreatmentAdministrationController.ActionRequest("late", "SKIPPED"), authentication));
    }

    @Test
    void scheduledAdministrationCanBeMarkedSkipped() {
        Authentication authentication = Mockito.mock(Authentication.class);
        when(authentication.getName()).thenReturn("vet");
        var result = controller.setStatus(animalId, treatmentId, administrationId,
            new TreatmentAdministrationController.ActionRequest("not given", "SKIPPED"), authentication);

        org.junit.jupiter.api.Assertions.assertEquals("SKIPPED", result.getStatus());
        verify(administrations).save(administration);
    }
}

