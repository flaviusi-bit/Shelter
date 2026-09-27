package com.shelter.api.document;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DocumentStorageServiceTest {
    @TempDir Path tempDir;

    @Test void storesAllowedImageType() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var file=new MockMultipartFile("file","photo.png","image/png",new byte[]{(byte)0x89,0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A});
        var stored=service.store(UUID.randomUUID(),file);
        assertEquals("photo.png",stored.originalFileName()); assertEquals("image/png",stored.contentType());
        assertTrue(service.resolve(stored.storageKey()).toFile().isFile()); service.delete(stored.storageKey());
        assertFalse(service.resolve(stored.storageKey()).toFile().exists());
    }
    @Test void rejectsContentThatDoesNotMatchDeclaredPdfType() {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var file=new MockMultipartFile("file","payload.pdf","application/pdf","not a pdf".getBytes());
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File content does not match content type",error.getMessage());
    }
    @Test void acceptsPdfWithMatchingSignature() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var file=new MockMultipartFile("file","report.pdf","application/pdf","%PDF-1.7".getBytes());
        var stored=service.store(UUID.randomUUID(),file); assertTrue(service.resolve(stored.storageKey()).toFile().isFile());
    }
    @Test void acceptsDocxWithRequiredOfficeOpenXmlParts() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var file=new MockMultipartFile("file","report.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",minimalDocx());
        var stored=service.store(UUID.randomUUID(),file); assertTrue(service.resolve(stored.storageKey()).toFile().isFile());
    }
    @Test void rejectsZipThatIsNotAValidDocxPackage() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var file=new MockMultipartFile("file","payload.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",zipBytes("payload.txt","not a docx"));
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File content does not match content type",error.getMessage());
    }
    @Test void rejectsDocxWithInvalidRelationshipTargetMode() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var relationships="<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Target=\"word/document.xml\" TargetMode=\"Unexpected\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\"/></Relationships>";
        var file=new MockMultipartFile("file","payload.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                zipBytes("[Content_Types].xml","<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>",
                        "_rels/.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Target=\"word/document.xml\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\"/></Relationships>",
                        "word/document.xml","<document xmlns=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"/>",
                        "word/_rels/document.xml.rels",relationships));
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File content does not match content type",error.getMessage());
    }

    @Test void rejectsDocxWithDuplicateRelationshipId() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var relationships="<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Target=\"word/document.xml\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\"/><Relationship Id=\"rId1\" Target=\"word/document.xml\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\"/></Relationships>";
        var file=new MockMultipartFile("file","payload.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                zipBytes("[Content_Types].xml","<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>",
                        "_rels/.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Target=\"word/document.xml\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\"/></Relationships>",
                        "word/document.xml","<document xmlns=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"/>",
                        "word/_rels/document.xml.rels",relationships));
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File content does not match content type",error.getMessage());
    }

    @Test void rejectsDocxWithUnsafeInternalRelationshipTarget() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var relationships="<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Target=\"../evil.xml\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/attachedTemplate\"/></Relationships>";
        var file=new MockMultipartFile("file","payload.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                zipBytes("[Content_Types].xml","<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>",
                        "_rels/.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>",
                        "word/document.xml","<document xmlns=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"/>",
                        "word/_rels/document.xml.rels",relationships));
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File content does not match content type",error.getMessage());
    }

    @Test void rejectsDocxWithExternalDocumentRelationship() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var file=new MockMultipartFile("file","payload.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                zipBytes("[Content_Types].xml","<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>",
                        "_rels/.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>",
                        "word/document.xml","<document xmlns=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"/>",
                        "word/_rels/document.xml.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" TargetMode=\"External\" Target=\"https://example.com/template.dotx\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/attachedTemplate\"/></Relationships>"));
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File content does not match content type",error.getMessage());
    }

    @Test void rejectsDocxWithExtremeCompressionRatio() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        byte[] payload=new byte[2 * 1024 * 1024];
        var output=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(output)){ zip.putNextEntry(new ZipEntry("word/large.bin")); zip.write(payload); zip.closeEntry(); }
        var file=new MockMultipartFile("file","payload.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",output.toByteArray());
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File content does not match content type",error.getMessage());
    }

    @Test void rejectsDocxWithDoctypeInRequiredXml() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        String xml="<!DOCTYPE Types [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">&xxe;</Types>";
        var file=new MockMultipartFile("file","payload.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",zipBytes("[Content_Types].xml",xml,"word/document.xml","<document xmlns=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"/>"));
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File content does not match content type",error.getMessage());
    }

    @Test void rejectsDocxWithMalformedRequiredXml() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var file=new MockMultipartFile("file","payload.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",zipBytes("[Content_Types].xml","<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>","word/document.xml","<document>"));
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File content does not match content type",error.getMessage());
    }
    @Test void rejectsDocxWithDuplicateRequiredPart() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var file=new MockMultipartFile("file","payload.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",zipBytes("[Content_Types].xml","<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>","./[Content_Types].xml","<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>","word/document.xml","<document xmlns=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"/>"));
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File content does not match content type",error.getMessage());
    }
    @Test void rejectsExcessiveTotalUncompressedSizeFromDirectories() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var output=new java.io.ByteArrayOutputStream();
        try(var zip=new java.util.zip.ZipOutputStream(output)){
            for(int i=0;i<4;i++){ zip.putNextEntry(new java.util.zip.ZipEntry("dir"+i+"/")); zip.closeEntry(); }
        }
        var file=new MockMultipartFile("file","payload.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",output.toByteArray());
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File content does not match content type",error.getMessage());
    }
    @Test void rejectsDocxWithDuplicateNormalizedZipEntry() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var file=new MockMultipartFile("file","payload.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                zipBytes("[Content_Types].xml","<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>",
                        "word/document.xml","<document xmlns=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"/>",
                        "./word/document.xml","duplicate"));
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File content does not match content type",error.getMessage());
    }

    @Test void rejectsMalformedDocxZip() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        byte[] malformed="not-a-zip".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var file=new MockMultipartFile("file","payload.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",malformed);
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File content does not match content type",error.getMessage());
    }

    @Test void rejectsDocxWithExplicitCurrentDirectoryZipPrefix() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var output=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(output)){
            zip.putNextEntry(new ZipEntry("./word/document.xml"));
            zip.write("x".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        var file=new MockMultipartFile("file","payload.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",output.toByteArray());
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File content does not match content type",error.getMessage());
    }

    @Test void rejectsDocxWithTrailingDotOrSpaceInZipEntryPath() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        for (String entryName : new String[]{"word/document.xml.", "word/document.xml "}) {
            var output=new ByteArrayOutputStream();
            try(var zip=new ZipOutputStream(output)){
                zip.putNextEntry(new ZipEntry(entryName));
                zip.write("x".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            var file=new MockMultipartFile("file","payload.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",output.toByteArray());
            var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
            assertEquals("File content does not match content type",error.getMessage());
        }
    }

    @Test void rejectsDocxWithTildePrefixedZipEntryPath() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var output=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(output)){
            zip.putNextEntry(new ZipEntry("~word/document.xml"));
            zip.write("x".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        var file=new MockMultipartFile("file","payload.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",output.toByteArray());
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File content does not match content type",error.getMessage());
    }

    @Test void rejectsDocxWithAbsoluteZipEntryPath() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var output=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(output)){
            zip.putNextEntry(new ZipEntry("/word/document.xml"));
            zip.write("x".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        var file=new MockMultipartFile("file","payload.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",output.toByteArray());
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File content does not match content type",error.getMessage());
    }

    @Test void rejectsDocxWithDoubleDotInZipEntryName() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var output=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(output)){
            zip.putNextEntry(new ZipEntry("word..document.xml"));
            zip.write("x".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        var file=new MockMultipartFile("file","payload.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",output.toByteArray());
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File content does not match content type",error.getMessage());
    }

    @Test void rejectsDocxWithApostropheInZipEntryName() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var output=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(output)){
            zip.putNextEntry(new ZipEntry("word/doc'ument.xml"));
            zip.write("x".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        var file=new MockMultipartFile("file","payload.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",output.toByteArray());
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File content does not match content type",error.getMessage());
    }

    @Test void rejectsDocxWithQuoteInZipEntryName() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var output=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(output)){
            zip.putNextEntry(new ZipEntry("word/doc\"ument.xml"));
            zip.write("x".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        var file=new MockMultipartFile("file","payload.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",output.toByteArray());
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File content does not match content type",error.getMessage());
    }

    @Test void rejectsDocxWithAngleBracketInZipEntryName() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var output=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(output)){
            zip.putNextEntry(new ZipEntry("word/doc<ument>.xml"));
            zip.write("x".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        var file=new MockMultipartFile("file","payload.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",output.toByteArray());
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File content does not match content type",error.getMessage());
    }

    @Test void rejectsDocxWithPipeInZipEntryName() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var output=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(output)){
            zip.putNextEntry(new ZipEntry("word/doc|ument.xml"));
            zip.write("x".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        var file=new MockMultipartFile("file","payload.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",output.toByteArray());
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File content does not match content type",error.getMessage());
    }

    @Test void rejectsDocxWithAsteriskInZipEntryName() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var output=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(output)){
            zip.putNextEntry(new ZipEntry("word/doc*ument.xml"));
            zip.write("x".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        var file=new MockMultipartFile("file","payload.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",output.toByteArray());
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File content does not match content type",error.getMessage());
    }

    @Test void rejectsDocxWithQuestionMarkInZipEntryName() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var output=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(output)){
            zip.putNextEntry(new ZipEntry("word/doc?ument.xml"));
            zip.write("x".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        var file=new MockMultipartFile("file","payload.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",output.toByteArray());
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File content does not match content type",error.getMessage());
    }

    @Test void rejectsDocxWithBackslashInZipEntryName() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var output=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(output)){
            zip.putNextEntry(new ZipEntry("word\\document.xml"));
            zip.write("x".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        var file=new MockMultipartFile("file","payload.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",output.toByteArray());
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File content does not match content type",error.getMessage());
    }

    @Test void rejectsDocxWithPercentInZipEntryName() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var output=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(output)){
            zip.putNextEntry(new ZipEntry("word/%2e%2e/document.xml"));
            zip.write("x".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        var file=new MockMultipartFile("file","payload.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",output.toByteArray());
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File content does not match content type",error.getMessage());
    }

    @Test void rejectsDocxWithColonInZipEntryName() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var output=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(output)){
            zip.putNextEntry(new ZipEntry("word/stream:name.xml"));
            zip.write("x".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        var file=new MockMultipartFile("file","payload.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",output.toByteArray());
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File content does not match content type",error.getMessage());
    }

    @Test void rejectsDocxWithBomPrefixInZipEntryName() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var output=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(output)){
            zip.putNextEntry(new ZipEntry("\uFEFFword/document.xml"));
            zip.write("x".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        var file=new MockMultipartFile("file","payload.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",output.toByteArray());
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File content does not match content type",error.getMessage());
    }

    @Test void rejectsDocxWithNullCharacterInZipEntryName() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var output=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(output)){
            zip.putNextEntry(new ZipEntry("word/na\0me.xml"));
            zip.write("x".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        var file=new MockMultipartFile("file","payload.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",output.toByteArray());
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File content does not match content type",error.getMessage());
    }

    @Test void rejectsDocxWithOversizedZipEntryName() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var output=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(output)){
            zip.putNextEntry(new ZipEntry("word/"+"a".repeat(256)));
            zip.write("x".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        var file=new MockMultipartFile("file","payload.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",output.toByteArray());
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File content does not match content type",error.getMessage());
    }

    @Test void rejectsDocxWithBlankZipEntryName() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var output=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(output)){
            zip.putNextEntry(new ZipEntry(""));
            zip.closeEntry();
        }
        var file=new MockMultipartFile("file","payload.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",output.toByteArray());
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File content does not match content type",error.getMessage());
    }

    @Test void rejectsDocxWithDuplicateNormalizedDirectoryEntry() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var output=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(output)){
            zip.putNextEntry(new ZipEntry("word/"));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("./word/"));
            zip.closeEntry();
        }
        var file=new MockMultipartFile("file","payload.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",output.toByteArray());
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File content does not match content type",error.getMessage());
    }

    @Test void rejectsZipEntryWithControlCharacter() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var file=new MockMultipartFile("file","payload.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                zipBytes("[Content_Types].xml","<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>",
                        "word/\u0000document.xml","payload"));
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File content does not match content type",error.getMessage());
    }

    @Test void rejectsEmptyNormalizedZipEntry() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var output=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(output)){
            zip.putNextEntry(new ZipEntry("."));
            zip.write("payload".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        var file=new MockMultipartFile("file","payload.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",output.toByteArray());
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File content does not match content type",error.getMessage());
    }
    @Test void rejectsUnsafeDirectoryEntry() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var output=new java.io.ByteArrayOutputStream();
        try(var zip=new java.util.zip.ZipOutputStream(output)){ zip.putNextEntry(new java.util.zip.ZipEntry("../word/")); zip.closeEntry(); }
        var file=new MockMultipartFile("file","payload.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",output.toByteArray());
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File content does not match content type",error.getMessage());
    }
    @Test void rejectsUnsafeZipEntryWithExcessiveUncompressedSize() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        byte[] oversized=new byte[10 * 1024 * 1024 + 1];
        var output=new java.io.ByteArrayOutputStream();
        try(var zip=new java.util.zip.ZipOutputStream(output)){ zip.putNextEntry(new ZipEntry("../word/large.bin")); zip.write(oversized); zip.closeEntry(); }
        var file=new MockMultipartFile("file","payload.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",output.toByteArray());
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File content does not match content type",error.getMessage());
    }
    @Test void rejectsDocxWithExcessiveTotalUncompressedSize() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        byte[] payload=new byte[9 * 1024 * 1024];
        var output=new java.io.ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(output)){ for(int i=0;i<4;i++){ zip.putNextEntry(new ZipEntry("word/large"+i+".bin")); zip.write(payload); zip.closeEntry(); } }
        var file=new MockMultipartFile("file","payload.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",output.toByteArray());
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File content does not match content type",error.getMessage());
    }
    @Test void rejectsDocxWithOversizedUndeclaredZipEntry() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        byte[] oversized=new byte[10 * 1024 * 1024 + 1];
        var output=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(output)){ zip.putNextEntry(new ZipEntry("word/large.bin")); zip.write(oversized); zip.closeEntry(); }
        var file=new MockMultipartFile("file","payload.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",output.toByteArray());
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File content does not match content type",error.getMessage());
    }
    @Test void rejectsDocxWithOversizedZipEntry() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        byte[] oversized=new byte[10 * 1024 * 1024 + 1];
        String[] entries={"word/large.bin",new String(oversized,StandardCharsets.ISO_8859_1)};
        var file=new MockMultipartFile("file","payload.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",zipBytes(entries));
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File content does not match content type",error.getMessage());
    }
    @Test void rejectsDocxWithTooManyZipEntries() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        String[] entries=new String[1002 * 2];
        for(int i=0;i<1002;i++){ entries[i*2]="part"+i+".xml"; entries[i*2+1]="x"; }
        var file=new MockMultipartFile("file","payload.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",zipBytes(entries));
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File content does not match content type",error.getMessage());
    }
    @Test void rejectsDocxWithUnsafeZipEntryPath() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var file=new MockMultipartFile("file","payload.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",zipBytes("[Content_Types].xml","<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>","../word/document.xml","<document xmlns=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"/>"));
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File content does not match content type",error.getMessage());
    }
    @Test void rejectsDocxWithDeclaredOversizedRequiredXmlEntry() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var output=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(output)){
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write(new byte[1024 * 1024 + 1]);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("<document xmlns=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        var file=new MockMultipartFile("file","payload.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",output.toByteArray());
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File content does not match content type",error.getMessage());
    }

    @Test void rejectsDocxWithUndeclaredOversizedRequiredXmlEntry() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        byte[] oversized=new byte[1024 * 1024 + 1];
        var output=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(output)){
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write(oversized);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("<document xmlns=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        var file=new MockMultipartFile("file","payload.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",output.toByteArray());
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File content does not match content type",error.getMessage());
    }

    @Test void rejectsDocxWithOversizedRequiredXmlEntry() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var oversized="<document>"+"x".repeat(1024*1024)+"</document>";
        var file=new MockMultipartFile("file","payload.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",zipBytes("[Content_Types].xml","<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>","word/document.xml",oversized));
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File content does not match content type",error.getMessage());
    }
    private byte[] minimalDocx() throws Exception {
        return zipBytes(
                "[Content_Types].xml",
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>",
                "_rels/.rels",
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>",
                "word/document.xml",
                "<document xmlns=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"/>");
    }
    private byte[] zipBytes(String... entries) throws Exception {
        var output=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(output)){ for(int i=0;i<entries.length;i+=2){ zip.putNextEntry(new ZipEntry(entries[i])); zip.write(entries[i+1].getBytes(StandardCharsets.UTF_8)); zip.closeEntry(); } }
        return output.toByteArray();
    }
    @Test void rejectsSvgEvenThoughItIsAnImageMimeType() {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var file=new MockMultipartFile("file","payload.svg","image/svg+xml","<svg/>".getBytes());
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File type is not allowed",error.getMessage());
    }
    @Test void rejectsUnsupportedImageType() {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var file=new MockMultipartFile("file","payload.tiff","image/tiff",new byte[]{1,2,3});
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File type is not allowed",error.getMessage());
    }
    @Test void rejectsMismatchedExtensionAndContentType() {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var file=new MockMultipartFile("file","payload.exe","application/pdf",new byte[]{1,2,3});
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File extension does not match content type",error.getMessage());
    }
    @Test void rejectsAllowedContentTypeWithoutRequiredExtension() {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var file=new MockMultipartFile("file","payload","text/plain",new byte[]{1,2,3});
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File extension does not match content type",error.getMessage());
    }
    @Test void rejectsPathTraversalKey() {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        assertThrows(IllegalArgumentException.class,()->service.resolve("../outside.txt"));
        assertThrows(IllegalArgumentException.class,()->service.resolve(""));
    }
    @Test void rejectsNullAnimalId() {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var file=new MockMultipartFile("file","payload.txt","text/plain","hello".getBytes());
        var error=assertThrows(IllegalArgumentException.class,()->service.store(null,file));
        assertEquals("Animal id is required",error.getMessage());
    }
    @Test void rejectsOverlongFilename() {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var file=new MockMultipartFile("file","a".repeat(256)+".txt","text/plain","hello".getBytes());
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("Filename is too long",error.getMessage());
    }
    @Test void rejectsMissingContentType() {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var file=new MockMultipartFile("file","payload.bin",null,new byte[]{1,2,3});
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File type is not allowed",error.getMessage());
    }
}