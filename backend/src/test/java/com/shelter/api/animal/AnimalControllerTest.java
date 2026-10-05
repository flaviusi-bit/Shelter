package com.shelter.api.animal;

import com.shelter.api.audit.AuditLogService;
import com.shelter.api.document.DocumentStorageService;
import com.shelter.api.document.MedicalDocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AnimalControllerTest {

    @Test
    void rejectsDateOfBirthInTheFuture() {
        AnimalRepository repository = mock(AnimalRepository.class);
        AuditLogService audit = mock(AuditLogService.class);
        DocumentStorageService storage = mock(DocumentStorageService.class);
        MedicalDocumentRepository documents = mock(MedicalDocumentRepository.class);
        Authentication authentication = mock(Authentication.class);
        AnimalController controller = new AnimalController(repository, audit, storage, documents);

        var request = new AnimalController.AnimalRequest(
            "Misha", "DOG", "FEMALE",
            LocalDate.now().plusDays(1),
            null, null,
            LocalDate.now(),
            null, null, null, null, null);

        var ex = assertThrows(ResponseStatusException.class,
            () -> controller.create(request, authentication));

        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(repository, never()).save(any(Animal.class));
        verifyNoInteractions(audit);
    }

    @Test
    void rejectsUnknownAnimalStatus() {
        var validator = jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();
        var request = new AnimalController.AnimalRequest(
            "Misha", "DOG", "FEMALE",
            LocalDate.of(2020, 1, 1),
            null, null,
            LocalDate.of(2024, 1, 1),
            null, null, "DELETED", null, null);

        var violations = validator.validate(request);

        assertEquals(1, violations.size());
        assertEquals("must match \"ACTIVE|TREATMENT|HEALTHY|QUARANTINE|FOSTER|ADOPTED\"",
            violations.iterator().next().getMessage());
    }

    @Test
    void rejectsUnknownAnimalType() {
        var validator = jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();
        var request = new AnimalController.AnimalRequest(
            "Misha", "HORSE", "FEMALE",
            LocalDate.of(2020, 1, 1),
            null, null,
            LocalDate.of(2024, 1, 1),
            null, null, null, null, null);
        var violations = validator.validate(request);
        assertEquals(1, violations.size());
    }

    @Test
    void rejectsUnknownAnimalSex() {
        var validator = jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();
        var request = new AnimalController.AnimalRequest(
            "Misha", "DOG", "UNKNOWN_SEX",
            LocalDate.of(2020, 1, 1),
            null, null,
            LocalDate.of(2024, 1, 1),
            null, null, null, null, null);
        var violations = validator.validate(request);
        assertEquals(1, violations.size());
    }
}
