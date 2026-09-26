package com.shelter.api.document;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.UUID;

@Service
public class DocumentStorageService {
    private final Path root;
    public DocumentStorageService(@Value("${shelter.storage.documents-path:./data/documents}") String path) {
        this.root=Paths.get(path).toAbsolutePath().normalize();
    }
    public StoredFile store(UUID animalId, MultipartFile file) throws IOException {
        if(file==null||file.isEmpty()) throw new IllegalArgumentException("File is empty");
        if(file.getSize()>25L*1024*1024) throw new IllegalArgumentException("Maximum file size is 25 MB");
        String original=StringUtils.cleanPath(file.getOriginalFilename()==null?"document":file.getOriginalFilename());
        if(original.contains("..")) throw new IllegalArgumentException("Invalid filename");
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
        try (var in = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                if (entry.getName().equals("[Content_Types].xml")) contentTypes = true;
                if (entry.getName().equals("word/document.xml")) document = true;
                if (contentTypes && document) return true;
            }
        }
        return false;
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