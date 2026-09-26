package com.shelter.api.document;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DocumentStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void storesAllowedImageType() throws Exception {
        DocumentStorageService service = new DocumentStorageService(tempDir.toString());
        var file = new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1, 2, 3});

        var stored = service.store(UUID.randomUUID(), file);

        assertEquals("photo.png", stored.originalFileName());
        assertEquals("image/png", stored.contentType());
        assertTrue(service.resolve(stored.storageKey()).toFile().isFile());

        service.delete(stored.storageKey());
        assertFalse(service.resolve(stored.storageKey()).toFile().exists());
    }

    @Test
    void rejectsSvgEvenThoughItIsAnImageMimeType() {
        DocumentStorageService service = new DocumentStorageService(tempDir.toString());
        var file = new MockMultipartFile("file", "payload.svg", "image/svg+xml", "<svg/>".getBytes());

        var error = assertThrows(IllegalArgumentException.class,
            () -> service.store(UUID.randomUUID(), file));

        assertEquals("File type is not allowed", error.getMessage());
    }

    @Test
    void rejectsUnsupportedImageType() {
        DocumentStorageService service = new DocumentStorageService(tempDir.toString());
        var file = new MockMultipartFile("file", "payload.tiff", "image/tiff", new byte[]{1, 2, 3});

        var error = assertThrows(IllegalArgumentException.class,
            () -> service.store(UUID.randomUUID(), file));

        assertEquals("File type is not allowed", error.getMessage());
    }
    @Test
    void rejectsMismatchedExtensionAndContentType() {
        DocumentStorageService service = new DocumentStorageService(tempDir.toString());
        var file = new MockMultipartFile("file", "payload.exe", "application/pdf", new byte[]{1, 2, 3});

        var error = assertThrows(IllegalArgumentException.class,
            () -> service.store(UUID.randomUUID(), file));

        assertEquals("File extension does not match content type", error.getMessage());
    }

    @Test
    void rejectsAllowedContentTypeWithoutRequiredExtension() {
        DocumentStorageService service = new DocumentStorageService(tempDir.toString());
        var file = new MockMultipartFile("file", "payload", "text/plain", new byte[]{1, 2, 3});

        var error = assertThrows(IllegalArgumentException.class,
            () -> service.store(UUID.randomUUID(), file));

        assertEquals("File extension does not match content type", error.getMessage());
    }

    @Test
    void rejectsPathTraversalKey() {
        DocumentStorageService service = new DocumentStorageService(tempDir.toString());

        assertThrows(IllegalArgumentException.class, () -> service.resolve("../outside.txt"));
        assertThrows(IllegalArgumentException.class, () -> service.resolve(""));
    }

    @Test
    void rejectsMissingContentType() {
        DocumentStorageService service = new DocumentStorageService(tempDir.toString());
        var file = new MockMultipartFile("file", "payload.bin", null, new byte[]{1, 2, 3});

        var error = assertThrows(IllegalArgumentException.class,
            () -> service.store(UUID.randomUUID(), file));

        assertEquals("File type is not allowed", error.getMessage());
    }

}
