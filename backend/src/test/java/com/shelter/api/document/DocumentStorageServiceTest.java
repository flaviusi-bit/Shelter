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
    @Test void rejectsDocxWithMalformedRequiredXml() throws Exception {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var file=new MockMultipartFile("file","payload.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",zipBytes("[Content_Types].xml","<Types/>","word/document.xml","<document>"));
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File content does not match content type",error.getMessage());
    }
    private byte[] minimalDocx() throws Exception { return zipBytes("[Content_Types].xml","<Types/>","word/document.xml","<document/>"); }
    private byte[] zipBytes(String... entries) throws Exception {
        var output=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(output)){
            for(int i=0;i<entries.length;i+=2){ zip.putNextEntry(new ZipEntry(entries[i])); zip.write(entries[i+1].getBytes(StandardCharsets.UTF_8)); zip.closeEntry(); }
        }
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
    @Test void rejectsMissingContentType() {
        DocumentStorageService service=new DocumentStorageService(tempDir.toString());
        var file=new MockMultipartFile("file","payload.bin",null,new byte[]{1,2,3});
        var error=assertThrows(IllegalArgumentException.class,()->service.store(UUID.randomUUID(),file));
        assertEquals("File type is not allowed",error.getMessage());
    }
}