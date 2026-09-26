package com.shelter.api.treatment;

import com.shelter.api.animal.Animal;
import com.shelter.api.animal.AnimalRepository;
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
        controller = new TreatmentAdministrationController(administrations, treatments, animals, "Europe/Bucharest");

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
                new TreatmentAdministrationController.ActionRequest("late", "SKIPPED")));
    }

    @Test
    void scheduledAdministrationCanBeMarkedSkipped() {
        var result = controller.setStatus(animalId, treatmentId, administrationId,
            new TreatmentAdministrationController.ActionRequest("not given", "SKIPPED"));

        org.junit.jupiter.api.Assertions.assertEquals("SKIPPED", result.getStatus());
        verify(administrations).save(administration);
    }
}

