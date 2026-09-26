package com.shelter.api.document;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.*;
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
        String key=animalId+"/"+UUID.randomUUID()+ext;
        Path target=root.resolve(key).normalize(); if(!target.startsWith(root)) throw new IllegalArgumentException("Invalid storage path");
        Files.createDirectories(target.getParent()); file.transferTo(target);
        return new StoredFile(key,original,type,file.getSize());
    }
    private boolean isSafeImageType(String type) {
        return type.equals("image/jpeg")
                || type.equals("image/png")
                || type.equals("image/gif")
                || type.equals("image/webp")
                || type.equals("image/bmp");
    }

    public Path resolve(String key){Path p=root.resolve(key).normalize();if(!p.startsWith(root))throw new IllegalArgumentException("Invalid storage path");return p;}
    public record StoredFile(String storageKey,String originalFileName,String contentType,long size){}
}