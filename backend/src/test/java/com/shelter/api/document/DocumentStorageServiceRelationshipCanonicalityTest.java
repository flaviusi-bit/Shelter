package com.shelter.api.document;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class DocumentStorageServiceRelationshipCanonicalityTest {
    @TempDir Path tempDir;

    @Test void rejectsDocxWithCurrentDirectorySegmentInRootRelationshipTarget() throws Exception {
        DocumentStorageService service = new DocumentStorageService(tempDir.toString());
        String rootRelationships = "<Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'>"
                + "<Relationship Id='rId1' Target='./word/document.xml' "
                + "Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument'/>"
                + "</Relationships>";
        var file = new MockMultipartFile("file", "payload.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                zipBytes(
                        "[Content_Types].xml",
                        "<Types xmlns='http://schemas.openxmlformats.org/package/2006/content-types'>"
                                + "<Override PartName='/word/document.xml' "
                                + "ContentType='application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml'/>"
                                + "</Types>",
                        "_rels/.rels", rootRelationships,
                        "word/document.xml",
                        "<document xmlns='http://schemas.openxmlformats.org/wordprocessingml/2006/main'/>"));
        var error = assertThrows(IllegalArgumentException.class, () -> service.store(UUID.randomUUID(), file));
        assertEquals("File content does not match content type", error.getMessage());
    }

    @Test void rejectsDocxWithCurrentDirectorySegmentInNestedRelationshipTarget() throws Exception {
        DocumentStorageService service = new DocumentStorageService(tempDir.toString());
        String nestedRelationships = "<Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'>"
                + "<Relationship Id='rId1' Target='./document.xml' "
                + "Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink'/>"
                + "</Relationships>";
        var file = new MockMultipartFile("file", "payload.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                zipBytes(
                        "[Content_Types].xml",
                        "<Types xmlns='http://schemas.openxmlformats.org/package/2006/content-types'>"
                                + "<Override PartName='/word/document.xml' "
                                + "ContentType='application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml'/>"
                                + "</Types>",
                        "_rels/.rels",
                        "<Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'>"
                                + "<Relationship Id='rId1' Target='word/document.xml' "
                                + "Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument'/>"
                                + "</Relationships>",
                        "word/document.xml",
                        "<document xmlns='http://schemas.openxmlformats.org/wordprocessingml/2006/main'/>",
                        "word/_rels/document.xml.rels", nestedRelationships));
        var error = assertThrows(IllegalArgumentException.class, () -> service.store(UUID.randomUUID(), file));
        assertEquals("File content does not match content type", error.getMessage());
    }


    @Test void rejectsDocxWithEmptyEntryPathSegment() throws Exception {
        DocumentStorageService service = new DocumentStorageService(tempDir.toString());
        var file = new MockMultipartFile("file", "payload.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                zipBytes(
                        "[Content_Types].xml",
                        "<Types xmlns='http://schemas.openxmlformats.org/package/2006/content-types'>"
                                + "<Override PartName='/word/document.xml' "
                                + "ContentType='application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml'/>"
                                + "</Types>",
                        "_rels/.rels",
                        "<Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'>"
                                + "<Relationship Id='rId1' Target='word/document.xml' "
                                + "Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument'/>"
                                + "</Relationships>",
                        "word//document.xml",
                        "<document xmlns='http://schemas.openxmlformats.org/wordprocessingml/2006/main'/>"));
        var error = assertThrows(IllegalArgumentException.class, () -> service.store(UUID.randomUUID(), file));
        assertEquals("File content does not match content type", error.getMessage());
    }

    @Test void rejectsDocxWithEmptyRelationshipTargetPathSegment() throws Exception {
        DocumentStorageService service = new DocumentStorageService(tempDir.toString());
        String rootRelationships = "<Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'>"
                + "<Relationship Id='rId1' Target='word//document.xml' "
                + "Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument'/>"
                + "</Relationships>";
        var file = new MockMultipartFile("file", "payload.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                zipBytes(
                        "[Content_Types].xml",
                        "<Types xmlns='http://schemas.openxmlformats.org/package/2006/content-types'>"
                                + "<Override PartName='/word/document.xml' "
                                + "ContentType='application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml'/>"
                                + "</Types>",
                        "_rels/.rels", rootRelationships,
                        "word/document.xml",
                        "<document xmlns='http://schemas.openxmlformats.org/wordprocessingml/2006/main'/>"));
        var error = assertThrows(IllegalArgumentException.class, () -> service.store(UUID.randomUUID(), file));
        assertEquals("File content does not match content type", error.getMessage());
    }

    private byte[] zipBytes(String... entries) throws Exception {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            for (int i = 0; i < entries.length; i += 2) {
                zip.putNextEntry(new ZipEntry(entries[i]));
                zip.write(entries[i + 1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
