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
    private static final long MAX_DOCX_COMPRESSION_RATIO = 100L;
    private static final long MIN_DOCX_RATIO_CHECK_SIZE = 1024L * 1024L;
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
        byte[] rootRelationshipsXml = null;
        int entryCount = 0;
        Set<String> entryNames = new HashSet<>();
        var relationshipParts = new java.util.ArrayList<String[]>();
        long[] totalEntrySize = {0};
        try (var in = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (++entryCount > MAX_DOCX_ENTRIES) return false;
                if (entry.getMethod() != ZipEntry.STORED && entry.getMethod() != ZipEntry.DEFLATED) return false;
                if (entry.getName() == null || entry.getName().isBlank()) return false;
                if (entry.getName().length() > 255) return false;
                long declaredSize = entry.getSize();
                long declaredCompressedSize = entry.getCompressedSize();
                if (declaredSize < -1L || declaredCompressedSize < -1L) return false;
                if (declaredSize > 0L && declaredCompressedSize == 0L) return false;
                if (declaredSize >= MIN_DOCX_RATIO_CHECK_SIZE && declaredCompressedSize > 0L
                        && declaredSize / declaredCompressedSize > MAX_DOCX_COMPRESSION_RATIO) return false;
                if (entry.getName().indexOf('\0') >= 0) return false;
                if (entry.getName().charAt(0) == '\uFEFF') return false;
                if (entry.getName().indexOf(':') >= 0) return false;
                if (entry.getName().indexOf('%') >= 0) return false;
                if (entry.getName().indexOf('\u0000') >= 0) return false;
                if (entry.getName().indexOf('\\') >= 0) return false;
                if (entry.getName().indexOf('?') >= 0) return false;
                if (entry.getName().indexOf('*') >= 0) return false;
                if (entry.getName().indexOf('|') >= 0) return false;
                if (entry.getName().indexOf('<') >= 0 || entry.getName().indexOf('>') >= 0) return false;
                if (entry.getName().indexOf('"') >= 0) return false;
                if (entry.getName().indexOf('\'') >= 0) return false;
                if (entry.getName().contains("..")) return false;
                if (entry.getName().startsWith("/")) return false;
                if (entry.getName().startsWith("~")) return false;
                if (entry.getName().endsWith(".") || entry.getName().endsWith(" ")) return false;
                if (entry.getName().indexOf('/') >= 0 && entry.getName().startsWith("./")) return false;
                if (entry.getName().indexOf('/') >= 0 && entry.getName().startsWith("//")) return false;
                if (containsUnicodePathConfusable(entry.getName())) return false;
                if (containsWindowsReservedPathSegment(entry.getName())) return false;
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
                    byte[] xml = readEntry(in, totalEntrySize);
                    contentTypes = isWellFormedXml(xml, "Types", "http://schemas.openxmlformats.org/package/2006/content-types")
                            && isValidContentTypes(xml);
                } else if (entryName.equals("_rels/.rels")) {
                    if (entry.getSize() > MAX_DOCX_XML_ENTRY_SIZE) return false;
                    if (rootRelationshipsXml != null) duplicateRequiredPart = true;
                    rootRelationshipsXml = readEntry(in, totalEntrySize);
                } else if (entryName.endsWith(".rels")) {
                    if (entry.getSize() > MAX_DOCX_XML_ENTRY_SIZE) return false;
                    byte[] relationshipsXml = readEntry(in, totalEntrySize);
                    if (!isWellFormedRelationshipsXml(relationshipsXml)) return false;
                    if (!"_rels/.rels".equals(entryName)) {
                        relationshipParts.add(new String[]{entryName, java.util.Base64.getEncoder().encodeToString(relationshipsXml)});
                    }
                } else if (entryName.equals("word/document.xml")) {
                    if (entry.getSize() > MAX_DOCX_XML_ENTRY_SIZE) return false;
                    if (document) duplicateRequiredPart = true;
                    document = isWellFormedXml(readEntry(in, totalEntrySize), "document", "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "http://purl.oclc.org/ooxml/wordprocessingml/main");
                } else if (!consumeEntryWithinLimit(in, totalEntrySize)) {
                    return false;
                }
            }
        } catch (java.util.zip.ZipException e) {
            return false;
        }
        if (!contentTypes || !document || rootRelationshipsXml == null
                || !isValidRootRelationships(rootRelationshipsXml)
                || duplicateRequiredPart || unsafeEntryPath) return false;
        if (!areInternalRelationshipTargetsPresent("_rels/.rels", rootRelationshipsXml, entryNames)) return false;
        for (String[] relationshipPart : relationshipParts) {
            byte[] xml;
            try {
                xml = java.util.Base64.getDecoder().decode(relationshipPart[1]);
            } catch (IllegalArgumentException e) {
                return false;
            }
            if (!areInternalRelationshipTargetsPresent(relationshipPart[0], xml, entryNames)) return false;
        }
        return true;
    }
    private boolean areInternalRelationshipTargetsPresent(String relationshipsPart, byte[] input, Set<String> entryNames) throws IOException {
        if (input == null) return false;
        try {
            var root = secureXmlFactory().newDocumentBuilder().parse(new ByteArrayInputStream(input)).getDocumentElement();
            var nodes = root.getElementsByTagNameNS("http://schemas.openxmlformats.org/package/2006/relationships", "Relationship");
            String sourcePart = relationshipSourcePart(relationshipsPart);
            for (int i = 0; i < nodes.getLength(); i++) {
                var node = nodes.item(i);
                var mode = node.getAttributes().getNamedItem("TargetMode");
                var target = node.getAttributes().getNamedItem("Target");
                if (mode != null && "External".equalsIgnoreCase(mode.getNodeValue())) continue;
                if (target == null || target.getNodeValue().isBlank()) return false;
                String resolved = resolveRelationshipTarget(sourcePart, target.getNodeValue());
                if (resolved == null || !entryNames.contains(resolved)) return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String relationshipSourcePart(String relationshipsPart) {
        if ("_rels/.rels".equals(relationshipsPart)) return "";
        if (relationshipsPart == null || !relationshipsPart.endsWith(".rels")) return null;
        int relsMarker = relationshipsPart.indexOf("/_rels/");
        if (relsMarker <= 0) return null;
        String source = relationshipsPart.substring(0, relsMarker + 1)
                + relationshipsPart.substring(relsMarker + 7, relationshipsPart.length() - 5);
        return source.isBlank() ? null : source;
    }

    private String resolveRelationshipTarget(String sourcePart, String target) {
        if (target.startsWith("/") || target.startsWith("\\")
                || target.contains("..") || target.contains("%")
                || target.contains(":") || target.indexOf('\\') >= 0
                || target.chars().anyMatch(Character::isISOControl)
                || containsUnicodePathConfusable(target)) return null;
        String combined = sourcePart == null || sourcePart.isEmpty()
                ? target
                : Paths.get(sourcePart).getParent().resolve(target).normalize().toString().replace('\\', '/');
        if (combined.isEmpty() || combined.startsWith("../") || combined.equals("..")) return null;
        return combined;
    }

    private boolean isValidContentTypes(byte[] input) throws IOException {
        if (input == null) return false;
        try {
            var root = secureXmlFactory().newDocumentBuilder().parse(new ByteArrayInputStream(input)).getDocumentElement();
            var nodes = root.getElementsByTagNameNS("http://schemas.openxmlformats.org/package/2006/content-types", "Override");
            for (int i = 0; i < nodes.getLength(); i++) {
                var node = nodes.item(i);
                var part = node.getAttributes().getNamedItem("PartName");
                var type = node.getAttributes().getNamedItem("ContentType");
                if (part != null && type != null
                        && "/word/document.xml".equals(part.getNodeValue())
                        && "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml".equals(type.getNodeValue())) return true;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    private boolean isValidRootRelationships(byte[] input) throws IOException {
        if (!isWellFormedRelationshipsXml(input)) return false;
        try {
            var root = secureXmlFactory().newDocumentBuilder().parse(new ByteArrayInputStream(input)).getDocumentElement();
            var nodes = root.getElementsByTagNameNS("http://schemas.openxmlformats.org/package/2006/relationships", "Relationship");
            for (int i = 0; i < nodes.getLength(); i++) {
                var node = nodes.item(i);
                var type = node.getAttributes().getNamedItem("Type");
                var target = node.getAttributes().getNamedItem("Target");
                if (type != null && target != null
                        && "http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument".equals(type.getNodeValue())
                        && "word/document.xml".equals(target.getNodeValue())) return true;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    private boolean isWellFormedRelationshipsXml(byte[] input) throws IOException {
        if (!isWellFormedXml(input, "Relationships", "http://schemas.openxmlformats.org/package/2006/relationships")) return false;
        try {
            var root = secureXmlFactory().newDocumentBuilder().parse(new ByteArrayInputStream(input)).getDocumentElement();
            var nodes = root.getElementsByTagNameNS("http://schemas.openxmlformats.org/package/2006/relationships", "Relationship");
            Set<String> relationshipIds = new HashSet<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                var node = nodes.item(i);
                var id = node.getAttributes().getNamedItem("Id");
                var type = node.getAttributes().getNamedItem("Type");
                var mode = node.getAttributes().getNamedItem("TargetMode");
                var target = node.getAttributes().getNamedItem("Target");
                if (id == null || id.getNodeValue().isBlank()
                        || !id.getNodeValue().matches("[A-Za-z_][A-Za-z0-9_.-]*")
                        || !relationshipIds.add(id.getNodeValue())) return false;
                if (type == null || type.getNodeValue().isBlank()) return false;
                if (mode != null && !"External".equalsIgnoreCase(mode.getNodeValue())
                        && !"Internal".equalsIgnoreCase(mode.getNodeValue())) return false;
                if (mode != null && "External".equalsIgnoreCase(mode.getNodeValue())) return false;
                if (target == null || target.getNodeValue().isBlank()) return false;
                String targetValue = target.getNodeValue();
                if (targetValue.chars().anyMatch(Character::isISOControl)) return false;
                if (targetValue.startsWith("/") || targetValue.startsWith("\\")
                        || targetValue.contains("..") || targetValue.contains("%")
                        || targetValue.contains(":") || targetValue.indexOf('\\') >= 0
                        || containsUnicodePathConfusable(targetValue)) return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private DocumentBuilderFactory secureXmlFactory() throws Exception {
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
        return factory;
    }

    private boolean containsUnicodePathConfusable(String value) {
        if (value == null) return false;
        return value.indexOf('\u2215') >= 0 || value.indexOf('\u2044') >= 0
                || value.indexOf('\u29F8') >= 0 || value.indexOf('\uFF0F') >= 0
                || value.indexOf('\uFF3C') >= 0 || value.indexOf('\u2216') >= 0
                || value.indexOf('\u2024') >= 0 || value.indexOf('\u2025') >= 0
                || value.indexOf('\u2026') >= 0 || value.indexOf('\uFF0E') >= 0;
    }

    private boolean containsWindowsReservedPathSegment(String value) {
        if (value == null) return false;
        for (String segment : value.split("/")) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) continue;
            String base = segment;
            int dot = base.indexOf('.');
            if (dot >= 0) base = base.substring(0, dot);
            if (base.equalsIgnoreCase("CON") || base.equalsIgnoreCase("PRN")
                    || base.equalsIgnoreCase("AUX") || base.equalsIgnoreCase("NUL")) return true;
            if (base.length() == 4 && (base.regionMatches(true, 0, "COM", 0, 3)
                    || base.regionMatches(true, 0, "LPT", 0, 3))
                    && base.charAt(3) >= '1' && base.charAt(3) <= '9') return true;
        }
        return false;
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
            var factory = secureXmlFactory();
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