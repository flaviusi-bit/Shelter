package com.shelter.api.document;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

@Service
public class DocumentStorageService {
    private static final int MAX_DOCX_XML_ENTRY_SIZE = 1024 * 1024;
    private static final int MAX_DOCX_ENTRIES = 1000;
    private static final long MAX_DOCX_ENTRY_SIZE = 10L * 1024 * 1024;
    private static final long MAX_DOCX_TOTAL_ENTRY_SIZE = 32L * 1024 * 1024;
    private final Path root;
    public DocumentStorageService(@Value("${shelter.storage.documents-path:./data/documents}") String path) {
        this.root=Paths.get(path).toAbsolutePath().normalize();
    }
    public StoredFile store(UUID animalId, MultipartFile file) throws IOException {
        if(animalId==null) throw new IllegalArgumentException("Animal id is required");
        if(file==null||file.isEmpty()) throw new IllegalArgumentException("File is empty");
        if(file.getSize()>25L*1024*1024) throw new IllegalArgumentException("Maximum file size is 25 MB");
        String original=StringUtils.cleanPath(file.getOriginalFilename()==null?"document":file.getOriginalFilename());
        if(original.contains("..")) throw new IllegalArgumentException("Invalid filename");
        if(original.length()>255) throw new IllegalArgumentException("Filename is too long");
        String type=file.getContentType()==null?"application/octet-stream":file.getContentType();
        if(!(type.equals("application/pdf")||isSafeImageType(type)||type.equals("text/plain")||type.equals("application/msword")||type.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))) throw new IllegalArgumentException("File type is not allowed");
        String ext=""; int dot=original.lastIndexOf('.'); if(dot>=0) ext=original.substring(dot).toLowerCase();
        if(!extensionMatchesContentType(ext,type)) throw new IllegalArgumentException("File extension does not match content type");
        if(!contentMatchesType(file,type)) throw new IllegalArgumentException("File content does not match content type");
        String key=animalId+"/"+UUID.randomUUID()+ext;
        Path target=root.resolve(key).normalize(); if(!target.startsWith(root)) throw new IllegalArgumentException("Invalid storage path");
        Files.createDirectories(target.getParent()); file.transferTo(target);
        return new StoredFile(key,original,type,file.getSize());
    }
    private boolean contentMatchesType(MultipartFile file,String type) throws IOException {
        if(type.equals("text/plain")) return true;
        byte[] header;
        try(var in=file.getInputStream()){ header=in.readNBytes(12); }
        return switch(type) {
            case "application/pdf" -> startsWith(header,new byte[]{0x25,0x50,0x44,0x46,0x2D});
            case "image/jpeg" -> startsWith(header,new byte[]{(byte)0xFF,(byte)0xD8,(byte)0xFF});
            case "image/png" -> startsWith(header,new byte[]{(byte)0x89,0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A});
            case "image/gif" -> startsWith(header,new byte[]{0x47,0x49,0x46,0x38});
            case "image/webp" -> startsWith(header,new byte[]{0x52,0x49,0x46,0x46}) && header.length>=12 && header[8]==0x57 && header[9]==0x45 && header[10]==0x42 && header[11]==0x50;
            case "image/bmp" -> startsWith(header,new byte[]{0x42,0x4D});
            case "application/msword" -> startsWith(header,new byte[]{(byte)0xD0,(byte)0xCF,0x11,(byte)0xE0,(byte)0xA1,(byte)0xB1,0x1A,(byte)0xE1});
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> isValidDocx(file);
            default -> false;
        };
    }
    private boolean isValidDocx(MultipartFile file) throws IOException {
        boolean contentTypes = false;
        boolean document = false;
        boolean duplicateRequiredPart = false;
        boolean unsafeEntryPath = false;
        int entryCount = 0;
        Set<String> entryNames = new HashSet<>();
        long[] totalEntrySize = {0};
        try (var in = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (++entryCount > MAX_DOCX_ENTRIES) return false;
                if (entry.getName() == null || entry.getName().isBlank()) return false;
                if (entry.getName().length() > 255) return false;
                if (entry.getName().indexOf('\0') >= 0) return false;
                if (entry.getName().charAt(0) == '\uFEFF') return false;
                if (entry.getName().indexOf(':') >= 0) return false;
                if (entry.getName().indexOf('%') >= 0) return false;
                if (entry.getName().indexOf('\\') >= 0) return false;
                if (entry.getName().indexOf('?') >= 0) return false;
                if (entry.getName().indexOf('*') >= 0) return false;
                if (entry.getName().indexOf('|') >= 0) return false;
                if (entry.getName().indexOf('<') >= 0 || entry.getName().indexOf('>') >= 0) return false;
                if (entry.getName().indexOf('"') >= 0) return false;
                if (entry.isDirectory()) {
                    String directoryName = normalizeZipEntryName(entry.getName());
                    if (directoryName == null) return false;
                    if (!entryNames.add(directoryName)) return false;
                    if (!consumeEntryWithinLimit(in, totalEntrySize)) return false;
                    continue;
                }
                if (entry.getSize() > MAX_DOCX_ENTRY_SIZE) return false;
                String entryName = normalizeZipEntryName(entry.getName());
                if (entryName == null) {
                    unsafeEntryPath = true;
                    if (!consumeEntryWithinLimit(in, totalEntrySize)) return false;
                    continue;
                }
                if (!entryNames.add(entryName)) return false;
                if (entryName.equals("[Content_Types].xml")) {
                    if (entry.getSize() > MAX_DOCX_XML_ENTRY_SIZE) return false;
                    if (contentTypes) duplicateRequiredPart = true;
                    contentTypes = isWellFormedXml(readEntry(in, totalEntrySize), "Types", "http://schemas.openxmlformats.org/package/2006/content-types");
                } else if (entryName.equals("word/document.xml")) {
                    if (entry.getSize() > MAX_DOCX_XML_ENTRY_SIZE) return false;
                    if (document) duplicateRequiredPart = true;
                    document = isWellFormedXml(readEntry(in, totalEntrySize), "document", "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "http://purl.oclc.org/ooxml/wordprocessingml/main");
                } else if (!consumeEntryWithinLimit(in, totalEntrySize)) {
                    return false;
                }
            }
        }
        return contentTypes && document && !duplicateRequiredPart && !unsafeEntryPath;
    }
    private String normalizeZipEntryName(String name) {
        if (name == null || name.isBlank()) return null;
        String normalized = name.replace('\\', '/');
        for (int i = 0; i < normalized.length(); i++) {
            if (Character.isISOControl(normalized.charAt(i))) return null;
        }
        if (normalized.startsWith("/")) return null;
        var stack = new java.util.ArrayDeque<String>();
        for (String part : normalized.split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) {
                if (stack.isEmpty()) return null;
                stack.removeLast();
            } else stack.addLast(part);
        }
        String result = String.join("/", stack);
        return result.isEmpty() ? null : result;
    }
    private boolean consumeEntryWithinLimit(java.io.InputStream input, long[] totalEntrySize) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            totalEntrySize[0] += read;
            if (total > MAX_DOCX_ENTRY_SIZE || totalEntrySize[0] > MAX_DOCX_TOTAL_ENTRY_SIZE) return false;
        }
        return true;
    }
    private byte[] readEntry(java.io.InputStream input, long[] totalEntrySize) throws IOException {
        var output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            totalEntrySize[0] += read;
            if (total > MAX_DOCX_XML_ENTRY_SIZE || totalEntrySize[0] > MAX_DOCX_TOTAL_ENTRY_SIZE) return null;
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }
    private boolean isWellFormedXml(byte[] input, String expectedRoot, String... expectedNamespaces) throws IOException {
        if (input == null) return false;
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var root = factory.newDocumentBuilder().parse(new ByteArrayInputStream(input)).getDocumentElement();
            String localName = root.getLocalName() != null ? root.getLocalName() : root.getNodeName();
            String namespace = root.getNamespaceURI();
            return expectedRoot.equals(localName) && java.util.Arrays.asList(expectedNamespaces).contains(namespace);
        } catch (Exception e) {
            return false;
        }
    }
    private boolean startsWith(byte[] value,byte[] prefix) {
        if(value.length<prefix.length) return false;
        for(int i=0;i<prefix.length;i++) if(value[i]!=prefix[i]) return false;
        return true;
    }
    private boolean extensionMatchesContentType(String ext,String type) {
        return switch(type) {
            case "application/pdf" -> ext.equals(".pdf");
            case "image/jpeg" -> ext.equals(".jpg")||ext.equals(".jpeg");
            case "image/png" -> ext.equals(".png");
            case "image/gif" -> ext.equals(".gif");
            case "image/webp" -> ext.equals(".webp");
            case "image/bmp" -> ext.equals(".bmp");
            case "text/plain" -> ext.equals(".txt");
            case "application/msword" -> ext.equals(".doc");
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> ext.equals(".docx");
            default -> false;
        };
    }
    private boolean isSafeImageType(String type) {
        return type.equals("image/jpeg") || type.equals("image/png") || type.equals("image/gif") || type.equals("image/webp") || type.equals("image/bmp");
    }
    public void delete(String key) throws IOException {
        if(key==null||key.isBlank()) return;
        Path p=resolve(key);
        Files.deleteIfExists(p);
    }
    public Path resolve(String key){
        if(key==null||key.isBlank()) throw new IllegalArgumentException("Invalid storage path");
        Path p=root.resolve(key).normalize();
        if(!p.startsWith(root)) throw new IllegalArgumentException("Invalid storage path");
        return p;
    }
    public record StoredFile(String storageKey,String originalFileName,String contentType,long size){}
}