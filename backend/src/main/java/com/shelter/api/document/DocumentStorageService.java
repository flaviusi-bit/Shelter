package com.shelter.api.document;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.Locale;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

@Service
public class DocumentStorageService {
    private static final int MAX_DOCX_XML_ENTRY_SIZE = 1024 * 1024;
    private static final int MAX_DOCX_ENTRIES = 1000;
    private static final int MAX_DOCX_RELATIONSHIPS = 1000;
    private static final int MAX_DOCX_CONTENT_TYPE_OVERRIDES = 1000;
    private static final int MAX_DOCX_RELATIONSHIP_ID_LENGTH = 255;
    private static final int MAX_DOCX_RELATIONSHIP_TYPE_LENGTH = 1024;
    private static final int MAX_DOCX_RELATIONSHIP_TARGET_LENGTH = 2048;
    private static final int MAX_DOCX_RELATIONSHIP_TYPE_URI_LENGTH = 512;
    private static final int MAX_DOCX_RELATIONSHIP_MODE_LENGTH = 32;
    private static final long MAX_DOCX_ENTRY_SIZE = 10L * 1024 * 1024;
    private static final long MAX_DOCX_TOTAL_ENTRY_SIZE = 32L * 1024 * 1024;
    private static final long MAX_DOCX_COMPRESSION_RATIO = 100L;
    private static final long MIN_DOCX_RATIO_CHECK_SIZE = 1024L * 1024L;
    private static final long MAX_FILE_SIZE = 25L * 1024L * 1024L;
    private final Path root;
    public DocumentStorageService(@Value("${shelter.storage.documents-path:./data/documents}") String path) {
        this.root=Paths.get(path).toAbsolutePath().normalize();
    }
    public StoredFile store(UUID animalId, MultipartFile file) throws IOException {
        if(animalId==null) throw new IllegalArgumentException("Animal id is required");
        if(file==null||file.isEmpty()) throw new IllegalArgumentException("File is empty");
        if(file.getSize()>MAX_FILE_SIZE) throw new IllegalArgumentException("Maximum file size is 25 MB");
        String original=StringUtils.cleanPath(file.getOriginalFilename()==null?"document":file.getOriginalFilename());
        if(original.isBlank() || original.chars().allMatch(ch -> ch == '/' || ch == '\\')) throw new IllegalArgumentException("Invalid filename");
        if(original.contains("..")) throw new IllegalArgumentException("Invalid filename");
        if(original.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Invalid filename");
        if (containsUnsafeFilenameCharacter(original)) throw new IllegalArgumentException("Invalid filename");
        if (containsWindowsReservedPathSegment(original) || containsWindowsTrailingDotOrSpaceSegment(original)) throw new IllegalArgumentException("Invalid filename");
        if(original.length()>255) throw new IllegalArgumentException("Filename is too long");
        String type=file.getContentType()==null?"application/octet-stream":file.getContentType();
        if(!(type.equals("application/pdf")||isSafeImageType(type)||type.equals("text/plain")||type.equals("application/msword")||type.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))) throw new IllegalArgumentException("File type is not allowed");
        String ext=""; int dot=original.lastIndexOf('.'); if(dot>=0) ext=original.substring(dot).toLowerCase(Locale.ROOT);
        if(!extensionMatchesContentType(ext,type)) throw new IllegalArgumentException("File extension does not match content type");
        if(!contentMatchesType(file,type)) throw new IllegalArgumentException("File content does not match content type");
        String key=generateStorageKey(animalId, ext);
        Path target=root.resolve(key).normalize(); if(!target.startsWith(root)) throw new IllegalArgumentException("Invalid storage path");
        try {
            Files.createDirectories(root);
        } catch (FileAlreadyExistsException e) {
            throw new IllegalArgumentException("Invalid storage path", e);
        }
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Invalid storage path");
        }
        Path realRoot = root.toRealPath();
        Path parent = target.getParent();
        Files.createDirectories(parent);
        Path realParent = parent.toRealPath();
        if (!realParent.startsWith(realRoot) || hasSymlinkComponent(root, parent)) {
            throw new IllegalArgumentException("Invalid storage path");
        }
        Path realTargetParent = target.getParent().toRealPath();
        if (!realTargetParent.equals(realParent)) {
            throw new IllegalArgumentException("Invalid storage path");
        }
        if (Files.isSymbolicLink(target)) {
            throw new IllegalArgumentException("Invalid storage path");
        }
        long storedSize;
        try (var input = file.getInputStream()) {
            try (var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                try {
                    storedSize = copyWithinLimit(input, output, MAX_FILE_SIZE);
                } catch (IllegalArgumentException | IOException e) {
                    try {
                        Files.deleteIfExists(target);
                    } catch (IOException cleanupException) {
                        e.addSuppressed(cleanupException);
                    }
                    throw e;
                }
            } catch (FileAlreadyExistsException e) {
                throw new IllegalArgumentException("Storage target already exists", e);
            }
        }
        return new StoredFile(key,original,type,storedSize);
    }
    String generateStorageKey(UUID animalId, String ext) { return animalId+"/"+UUID.randomUUID()+ext; }

    private long copyWithinLimit(java.io.InputStream input, java.io.OutputStream output, long maxBytes) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (total > maxBytes - read) throw new IllegalArgumentException("Maximum file size is 25 MB");
            output.write(buffer, 0, read);
            total += read;
        }
        return total;
    }

    static boolean exceedsCompressionRatio(long uncompressedSize, long compressedSize) {
        if (uncompressedSize < MIN_DOCX_RATIO_CHECK_SIZE || compressedSize <= 0L) return false;
        if (compressedSize > Long.MAX_VALUE / MAX_DOCX_COMPRESSION_RATIO) return false;
        return uncompressedSize > compressedSize * MAX_DOCX_COMPRESSION_RATIO;
    }

    private boolean hasSymlinkComponent(Path rootPath, Path targetParent) {
        Path current = rootPath;
        Path relative;
        try {
            relative = rootPath.relativize(targetParent);
        } catch (IllegalArgumentException e) {
            return true;
        }
        for (Path component : relative) {
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) return true;
        }
        return false;
    }

    private boolean contentMatchesType(MultipartFile file,String type) throws IOException {
        if(type.equals("text/plain")) return isSafePlainText(file);
        byte[] header;
        try(var in=file.getInputStream()){
            header = in.readNBytes(12);
        }
        if (header.length == 0) return false;
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
        Set<String> fileEntryNames = new HashSet<>();
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
                if (declaredSize > MAX_DOCX_ENTRY_SIZE) return false;
                if (declaredSize > 0L && declaredCompressedSize == 0L) return false;
                if (declaredSize >= MIN_DOCX_RATIO_CHECK_SIZE && declaredCompressedSize > 0L
                        && exceedsCompressionRatio(declaredSize, declaredCompressedSize)) return false;
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
                if (containsEmptyZipPathSegment(entry.getName(), entry.isDirectory())) return false;
                if (containsUnicodePathConfusable(entry.getName())) return false;
                if (containsUnsafePathCharacter(entry.getName())) return false;
                if (containsWindowsReservedPathSegment(entry.getName())) return false;
                if (containsWindowsTrailingDotOrSpaceSegment(entry.getName())) return false;
                if (entry.isDirectory()) {
                    if (declaredSize > 0L) return false;
                    String directoryName = normalizeZipEntryName(entry.getName());
                    if (directoryName == null) return false;
                    if (!entryNames.add(directoryName)) return false;
                    byte[] directoryBuffer = new byte[8192];
                    int read;
                    do {
                        read = in.read(directoryBuffer);
                        if (read > 0) return false;
                    } while (read != -1);
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
                if (!fileEntryNames.add(entryName)) return false;
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
                    if (!"_rels/.rels".equals(entryName)
                            && relationshipSourcePart(entryName) == null) return false;
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
        if (!areInternalRelationshipTargetsPresent("_rels/.rels", rootRelationshipsXml, fileEntryNames)) return false;
        for (String[] relationshipPart : relationshipParts) {
            byte[] xml;
            try {
                xml = java.util.Base64.getDecoder().decode(relationshipPart[1]);
            } catch (IllegalArgumentException e) {
                return false;
            }
            String sourcePart = relationshipSourcePart(relationshipPart[0]);
            if (sourcePart == null || !fileEntryNames.contains(sourcePart)) return false;
            if (!areInternalRelationshipTargetsPresent(relationshipPart[0], xml, fileEntryNames)) return false;
        }
        return true;
    }
    private boolean areInternalRelationshipTargetsPresent(String relationshipsPart, byte[] input, Set<String> entryNames) throws IOException {
        if (input == null) return false;
        try {
            var root = secureXmlFactory().newDocumentBuilder().parse(new ByteArrayInputStream(input)).getDocumentElement();
            var nodes = root.getElementsByTagNameNS("http://schemas.openxmlformats.org/package/2006/relationships", "Relationship");
            String sourcePart = relationshipSourcePart(relationshipsPart);
            if (nodes.getLength() > MAX_DOCX_RELATIONSHIPS) return false;
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
        if (relsMarker <= 0 || relsMarker != relationshipsPart.lastIndexOf("/_rels/")) return null;
        String relationshipFile = relationshipsPart.substring(relsMarker + 7, relationshipsPart.length() - 5);
        if (relationshipFile.isBlank() || relationshipFile.indexOf('/') >= 0) return null;
        String source = relationshipsPart.substring(0, relsMarker + 1) + relationshipFile;
        return source.isBlank() ? null : source;
    }

    private String resolveRelationshipTarget(String sourcePart, String target) {
        if (target.startsWith("/") || target.startsWith("\\")
                || target.contains("..") || target.contains("%")
                || target.contains(":") || target.indexOf('\\') >= 0
                || target.chars().anyMatch(Character::isISOControl)
                || containsUnsafePathCharacter(target)
                || containsCurrentDirectoryPathSegment(target)
                || containsEmptyPathSegment(target)
                || containsUnicodePathConfusable(target)
                || containsWindowsReservedPathSegment(target)
                || containsWindowsTrailingDotOrSpaceSegment(target)) return null;
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
            if (nodes.getLength() > MAX_DOCX_CONTENT_TYPE_OVERRIDES) return false;
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
            int officeDocumentRelationships = 0;
            if (nodes.getLength() > MAX_DOCX_RELATIONSHIPS) return false;
            for (int i = 0; i < nodes.getLength(); i++) {
                var node = nodes.item(i);
                var type = node.getAttributes().getNamedItem("Type");
                var target = node.getAttributes().getNamedItem("Target");
                if (type != null && target != null
                        && "http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument".equals(type.getNodeValue())) {
                    if (!"word/document.xml".equals(target.getNodeValue())) return false;
                    officeDocumentRelationships++;
                }
            }
            return officeDocumentRelationships == 1;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isWellFormedRelationshipsXml(byte[] input) throws IOException {
        if (!isWellFormedXml(input, "Relationships", "http://schemas.openxmlformats.org/package/2006/relationships")) return false;
        try {
            var root = secureXmlFactory().newDocumentBuilder().parse(new ByteArrayInputStream(input)).getDocumentElement();
            var nodes = root.getElementsByTagNameNS("http://schemas.openxmlformats.org/package/2006/relationships", "Relationship");
            var rootAttributes = root.getAttributes();
            for (int a = 0; a < rootAttributes.getLength(); a++) {
                var attribute = rootAttributes.item(a);
                if (attribute.getNamespaceURI() != null
                        && "http://www.w3.org/2000/xmlns/".equals(attribute.getNamespaceURI())) continue;
                return false;
            }
            var children = root.getChildNodes();
            Set<String> relationshipIds = new HashSet<>();
            for (int i = 0; i < children.getLength(); i++) {
                var child = children.item(i);
                if (child.getNodeType() == org.w3c.dom.Node.TEXT_NODE) {
                    if (!child.getNodeValue().isBlank()) return false;
                    continue;
                }
                if (child.getNodeType() == org.w3c.dom.Node.COMMENT_NODE) continue;
                if (child.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE
                        || !"http://schemas.openxmlformats.org/package/2006/relationships".equals(child.getNamespaceURI())
                        || !"Relationship".equals(child.getLocalName())) return false;
            }
            if (nodes.getLength() > MAX_DOCX_RELATIONSHIPS) return false;
            for (int i = 0; i < nodes.getLength(); i++) {
                var node = nodes.item(i);
                var id = node.getAttributes().getNamedItem("Id");
                var type = node.getAttributes().getNamedItem("Type");
                var mode = node.getAttributes().getNamedItem("TargetMode");
                var target = node.getAttributes().getNamedItem("Target");
                var attributes = node.getAttributes();
                for (int a = 0; a < attributes.getLength(); a++) {
                    var attribute = attributes.item(a);
                    if (attribute.getNamespaceURI() != null
                            || (!"Id".equals(attribute.getNodeName())
                            && !"Type".equals(attribute.getNodeName())
                            && !"Target".equals(attribute.getNodeName())
                            && !"TargetMode".equals(attribute.getNodeName()))) return false;
                }
                if (id == null || id.getNodeValue().isBlank()
                        || id.getNodeValue().length() > MAX_DOCX_RELATIONSHIP_ID_LENGTH
                        || !id.getNodeValue().matches("[A-Za-z_][A-Za-z0-9_.-]*")
                        || !relationshipIds.add(id.getNodeValue())) return false;
                if (type == null || type.getNodeValue().isBlank()
                        || type.getNodeValue().length() > MAX_DOCX_RELATIONSHIP_TYPE_LENGTH
                        || type.getNodeValue().length() > MAX_DOCX_RELATIONSHIP_TYPE_URI_LENGTH
                        || type.getNodeValue().chars().anyMatch(Character::isISOControl)
                        || type.getNodeValue().chars().anyMatch(Character::isWhitespace)
                        || !type.getNodeValue().contains(":")) return false;
                if (mode != null && (mode.getNodeValue().length() > MAX_DOCX_RELATIONSHIP_MODE_LENGTH
                        || !"External".equalsIgnoreCase(mode.getNodeValue())
                        && !"Internal".equalsIgnoreCase(mode.getNodeValue()))) return false;
                if (mode != null && "External".equalsIgnoreCase(mode.getNodeValue())) return false;
                if (target == null || target.getNodeValue().isBlank()
                        || target.getNodeValue().length() > MAX_DOCX_RELATIONSHIP_TARGET_LENGTH) return false;
                String targetValue = target.getNodeValue();
                if (targetValue.chars().anyMatch(Character::isISOControl)) return false;
                if (containsUnsafePathCharacter(targetValue) || containsCurrentDirectoryPathSegment(targetValue)
                        || containsEmptyPathSegment(targetValue)) return false;
                if (targetValue.startsWith("/") || targetValue.startsWith("\\")
                        || targetValue.contains("..") || targetValue.contains("%")
                        || targetValue.contains(":") || targetValue.indexOf('\\') >= 0
                        || containsUnicodePathConfusable(targetValue)
                        || containsWindowsReservedPathSegment(targetValue)
                        || containsWindowsTrailingDotOrSpaceSegment(targetValue)) return false;
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

    private boolean containsUnsafePathCharacter(String value) {
        if (value == null) return true;
        for (int i = 0; i < value.length();) {
            int codePoint = value.codePointAt(i);
            if (Character.getType(codePoint) == Character.FORMAT) return true;
            i += Character.charCount(codePoint);
        }
        return false;
    }

    private boolean containsEmptyZipPathSegment(String value, boolean directory) {
        if (value == null || value.isBlank()) return true;
        int length = value.length();
        if (directory) {
            if (!value.endsWith("/")) return true;
            length--;
        }
        if (length == 0) return true;
        for (int i = 0; i < length; i++) {
            if (value.charAt(i) == '/' && (i == 0 || value.charAt(i - 1) == '/')) return true;
        }
        return false;
    }

    private boolean containsEmptyPathSegment(String value) {
        if (value == null) return true;
        String[] segments = value.split("/", -1);
        for (int i = 0; i < segments.length; i++) {
            if (segments[i].isEmpty() && !(i == segments.length - 1 && i > 0)) return true;
        }
        return false;
    }

    private boolean containsCurrentDirectoryPathSegment(String value) {
        if (value == null) return true;
        for (String segment : value.split("/", -1)) {
            if (segment.equals(".")) return true;
        }
        return false;
    }

    private boolean containsUnsafeFilenameCharacter(String value) {
        if (value == null) return true;
        for (int i = 0; i < value.length();) {
            int codePoint = value.codePointAt(i);
            if (Character.getType(codePoint) == Character.FORMAT) return true;
            i += Character.charCount(codePoint);
        }
        return containsUnicodePathConfusable(value);
    }

    private boolean containsUnicodePathConfusable(String value) {
        if (value == null) return false;
        return value.indexOf('\u2215') >= 0 || value.indexOf('\u2044') >= 0
                || value.indexOf('\u29F8') >= 0 || value.indexOf('\uFF0F') >= 0
                || value.indexOf('\uFF3C') >= 0 || value.indexOf('\u2216') >= 0
                || value.indexOf('\u2024') >= 0 || value.indexOf('\u2025') >= 0
                || value.indexOf('\u2026') >= 0 || value.indexOf('\uFF0E') >= 0;
    }

    private boolean containsWindowsTrailingDotOrSpaceSegment(String value) {
        if (value == null) return true;
        for (String segment : value.split("/")) {
            if (!segment.isEmpty() && (segment.endsWith(".") || segment.endsWith(" "))) return true;
        }
        return false;
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
            if (part.isEmpty()) continue;
            if (part.equals(".")) return null;
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
            if (!addWithinLimit(total, read, MAX_DOCX_ENTRY_SIZE)) return false;
            if (!addWithinLimit(totalEntrySize[0], read, MAX_DOCX_TOTAL_ENTRY_SIZE)) return false;
            total += read;
            totalEntrySize[0] += read;
        }
        return true;
    }
    private byte[] readEntry(java.io.InputStream input, long[] totalEntrySize) throws IOException {
        var output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (!addWithinLimit(total, read, MAX_DOCX_XML_ENTRY_SIZE)) return null;
            if (!addWithinLimit(totalEntrySize[0], read, MAX_DOCX_TOTAL_ENTRY_SIZE)) return null;
            total += read;
            totalEntrySize[0] += read;
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    static boolean addWithinLimit(long current, long increment, long limit) {
        return increment >= 0L && current >= 0L && current <= limit && increment <= limit - current;
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
    private boolean isSafePlainText(MultipartFile file) throws IOException {
        try (var input = file.getInputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                for (int i = 0; i < read; i++) {
                    int value = buffer[i] & 0xFF;
                    if ((value < 0x20 && value != '\t' && value != '\n' && value != '\r') || value == 0x7F) {
                        return false;
                    }
                }
            }
        }
        try (var input = new InputStreamReader(file.getInputStream(),
                StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT))) {
            char[] buffer = new char[8192];
            while (input.read(buffer) != -1) {
                // Decode incrementally to avoid buffering the entire upload in memory.
            }
        } catch (IOException e) {
            return false;
        }
        return true;
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
        if(key==null||key.isBlank() || !isSafeStorageKey(key)) throw new IllegalArgumentException("Invalid storage path");
        Path p=root.resolve(key).normalize();
        if(!p.startsWith(root) || p.equals(root)) throw new IllegalArgumentException("Invalid storage path");
        if (Files.isSymbolicLink(root) || hasSymlinkComponent(root, p.getParent() == null ? root : p.getParent())
                || Files.isSymbolicLink(p)) {
            throw new IllegalArgumentException("Invalid storage path");
        }
        return p;
    }

    private boolean isSafeStorageKey(String key) {
        if (key.startsWith("/") || key.startsWith("\\") || key.endsWith("/")
                || key.contains("\\") || key.contains("//")
                || key.contains("..") || key.contains("%") || key.contains(":")
                || key.startsWith("~") || key.chars().anyMatch(Character::isISOControl)
                || containsUnsafePathCharacter(key)
                || containsUnicodePathConfusable(key)
                || containsWindowsReservedPathSegment(key)
                || containsWindowsTrailingDotOrSpaceSegment(key)) return false;
        for (String segment : key.split("/")) {
            if (segment.isBlank() || ".".equals(segment)) return false;
        }
        return true;
    }
    public record StoredFile(String storageKey,String originalFileName,String contentType,long size){}
}