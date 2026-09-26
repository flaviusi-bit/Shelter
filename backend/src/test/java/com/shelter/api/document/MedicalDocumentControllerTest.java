package com.shelter.api.document;

import com.shelter.api.animal.Animal;
import com.shelter.api.animal.AnimalRepository;
import com.shelter.api.audit.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MedicalDocumentControllerTest {
    @TempDir
    Path tempDir;

    private MedicalDocumentRepository documents;
    private AnimalRepository animals;
    private AuditLogService audit;
    private DocumentStorageService storage;
    private MedicalDocumentController controller;
    private Authentication authentication;
    private UUID animalId;
    private Animal animal;

    @BeforeEach
    void setUp() {
        documents = mock(MedicalDocumentRepository.class);
        animals = mock(AnimalRepository.class);
        audit = mock(AuditLogService.class);
        storage = new DocumentStorageService(tempDir.toString());
        controller = new MedicalDocumentController(documents, animals, storage, audit);

        animalId = UUID.randomUUID();
        animal = new Animal();
        animal.setId(animalId);
        when(animals.findById(animalId)).thenReturn(Optional.of(animal));

        authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("vet");
    }

    @Test
    void downloadReturnsStoredFileForOwningAnimal() throws Exception {
        UUID documentId = UUID.randomUUID();
        MedicalDocument document = document("report.pdf", "application/pdf", animalId);
        var stored = storage.store(animalId,
            new org.springframework.mock.web.MockMultipartFile(
                "file", "report.pdf", "application/pdf", "medical report".getBytes()));
        document.setStorageKey(stored.storageKey());
        document.setOriginalFileName(stored.originalFileName());
        document.setContentType(stored.contentType());

        when(documents.findById(documentId)).thenReturn(Optional.of(document));

        var response = controller.download(animalId, documentId, authentication);

        assertEquals("application/pdf", response.getHeaders().getContentType().toString());
        assertTrue(response.getBody().exists());
        assertEquals("inline; filename=\"report.pdf\"",
            response.getHeaders().getFirst("Content-Disposition"));
        verify(audit).record("vet", "ACCESS_MEDICAL_DOCUMENT", "MEDICAL_DOCUMENT",
            null, "report.pdf");
    }

    @Test
    void downloadRejectsDocumentBelongingToAnotherAnimal() {
        UUID documentId = UUID.randomUUID();
        UUID otherAnimalId = UUID.randomUUID();
        MedicalDocument document = document("private.pdf", "application/pdf", otherAnimalId);
        when(documents.findById(documentId)).thenReturn(Optional.of(document));

        var error = assertThrows(ResponseStatusException.class,
            () -> controller.download(animalId, documentId, authentication));

        assertEquals(404, error.getStatusCode().value());
        verify(audit, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void downloadRejectsMissingStoredFile() {
        UUID documentId = UUID.randomUUID();
        MedicalDocument document = document("missing.pdf", "application/pdf", animalId);
        document.setStorageKey("missing/" + UUID.randomUUID() + ".pdf");
        when(documents.findById(documentId)).thenReturn(Optional.of(document));

        var error = assertThrows(ResponseStatusException.class,
            () -> controller.download(animalId, documentId, authentication));

        assertEquals(404, error.getStatusCode().value());
        verify(audit, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void downloadSanitizesHeaderFilename() throws Exception {
        UUID documentId = UUID.randomUUID();
        String dangerousName = "report\r\nX-Injected: true\".pdf";
        MedicalDocument document = document(dangerousName, "application/pdf", animalId);
        var stored = storage.store(animalId,
            new org.springframework.mock.web.MockMultipartFile(
                "file", "safe.pdf", "application/pdf", "data".getBytes()));
        document.setStorageKey(stored.storageKey());
        document.setContentType("application/pdf");
        when(documents.findById(documentId)).thenReturn(Optional.of(document));

        var response = controller.download(animalId, documentId, authentication);

        String header = response.getHeaders().getFirst("Content-Disposition");
        assertNotNull(header);
        assertFalse(header.contains("\\r"));
        assertFalse(header.contains("\\n"));
        assertFalse(header.contains("\\\""));
        assertEquals("inline; filename=\"report__X-Injected: true_.pdf\"", header);
    }

    @Test
    void downloadSanitizesBackslashInHeaderFilename() throws Exception {
        UUID documentId = UUID.randomUUID();
        String dangerousName = "report\\\\server\\share\\report.pdf";
        MedicalDocument document = document(dangerousName, "application/pdf", animalId);
        var stored = storage.store(animalId,
            new org.springframework.mock.web.MockMultipartFile(
                "file", "safe.pdf", "application/pdf", "data".getBytes()));
        document.setStorageKey(stored.storageKey());
        document.setContentType("application/pdf");
        when(documents.findById(documentId)).thenReturn(Optional.of(document));

        var response = controller.download(animalId, documentId, authentication);

        String header = response.getHeaders().getFirst("Content-Disposition");
        assertNotNull(header);
        assertFalse(header.contains("\\\\"));
        assertEquals("inline; filename=\"report__server_share_report.pdf\"", header);
    }

    @Test
    void downloadRejectsInvalidStoredContentTypeWithoutAuditingAccess() throws Exception {
        UUID documentId = UUID.randomUUID();
        MedicalDocument document = document("report.pdf", "not-a-valid-media-type", animalId);
        var stored = storage.store(animalId,
            new org.springframework.mock.web.MockMultipartFile(
                "file", "report.pdf", "application/pdf", "medical report".getBytes()));
        document.setStorageKey(stored.storageKey());
        when(documents.findById(documentId)).thenReturn(Optional.of(document));

        var error = assertThrows(ResponseStatusException.class,
            () -> controller.download(animalId, documentId, authentication));

        assertEquals(404, error.getStatusCode().value());
        assertEquals("File unavailable", error.getReason());
        verify(audit, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void uploadRejectsInvalidDocumentDateBeforeWritingFile() {
        var file = new org.springframework.mock.web.MockMultipartFile(
            "file", "report.pdf", "application/pdf", "medical report".getBytes());

        var error = assertThrows(ResponseStatusException.class,
            () -> controller.upload(animalId, file, "LAB", "Blood test",
                "2026-99-99", null, authentication));

        assertEquals(400, error.getStatusCode().value());
        assertEquals("Document date must be a valid ISO date (YYYY-MM-DD)",
            error.getReason());
        verify(documents, never()).save(any(MedicalDocument.class));
        verify(audit, never()).record(any(), any(), any(), any(), any());
        assertEquals(0, tempDir.toFile().list().length);
    }

    @Test
    void uploadStoresFileAndPersistsMetadata() throws Exception {
        var file = new org.springframework.mock.web.MockMultipartFile(
            "file", "report.pdf", "application/pdf", "medical report".getBytes());

        when(documents.save(any(MedicalDocument.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var result = controller.upload(animalId, file, "LAB", "Blood test",
            "2026-09-26", "routine", authentication);

        assertNotNull(result);
        assertEquals(animal, result.getAnimal());
        assertEquals("LAB", result.getDocumentType());
        assertEquals("Blood test", result.getTitle());
        assertEquals("2026-09-26", result.getDocumentDate().toString());
        assertEquals("routine", result.getNotes());
        assertEquals("vet", result.getUploadedBy());
        assertEquals("application/pdf", result.getContentType());
        assertEquals(Long.valueOf("medical report".getBytes().length), result.getFileSize());
        assertTrue(result.getStorageKey().startsWith(animalId + "/"));
        assertTrue(storage.resolve(result.getStorageKey()).toFile().isFile());
        assertEquals("/api/animals/" + animalId + "/documents/files/null", result.getFileUrl());
        verify(documents, times(2)).save(result);
        verify(audit).record("vet", "UPLOAD_MEDICAL_DOCUMENT", "MEDICAL_DOCUMENT",
            null, "report.pdf");
    }

    @Test
    void uploadDeletesStoredFileWhenPersistenceFails() throws Exception {
        var file = new org.springframework.mock.web.MockMultipartFile(
            "file", "report.pdf", "application/pdf", "medical report".getBytes());

        when(documents.save(any(MedicalDocument.class)))
            .thenThrow(new RuntimeException("database unavailable"));

        assertThrows(RuntimeException.class,
            () -> controller.upload(animalId, file, "LAB", "Blood test",
                "2026-09-26", null, authentication));

        try (var paths = java.nio.file.Files.walk(tempDir)) {
            assertEquals(0, paths.filter(java.nio.file.Files::isRegularFile).count());
        }
        verify(audit, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void createRejectsOversizedNotesBeforeSaving() {
        MedicalDocument input = new MedicalDocument();
        input.setDocumentType("LAB");
        input.setTitle("Blood test");
        input.setNotes("x".repeat(10001));
        input.setFileUrl("/files/report.pdf");

        assertThrows(ResponseStatusException.class,
            () -> controller.create(animalId, input, authentication));

        verify(documents, never()).save(any(MedicalDocument.class));
        verify(audit, never()).record(any(), any(), any(), any(), any());
    }

    private MedicalDocument document(String name, String contentType, UUID ownerId) {
        Animal owner = new Animal();
        owner.setId(ownerId);
        MedicalDocument document = new MedicalDocument();
        document.setAnimal(owner);
        document.setOriginalFileName(name);
        document.setContentType(contentType);
        return document;
    }
}
