package com.shelter.api.document;

import com.shelter.api.animal.AnimalRepository;
import com.shelter.api.audit.AuditLogService;
import org.springframework.core.io.PathResource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import java.util.UUID;

@RestController
@RequestMapping("/api/animals/{animalId}/documents")
public class MedicalDocumentController {
    private final MedicalDocumentRepository documents;
    private final AnimalRepository animals;
    private final DocumentStorageService storage;
    private static final int MAX_DOCUMENTS_PER_ANIMAL = 200;
    private static final int MAX_TITLE_UTF8_BYTES = 800;
    private static final int MAX_NOTES_UTF8_BYTES = 40000;
    private final AuditLogService audit;

    public MedicalDocumentController(MedicalDocumentRepository documents,AnimalRepository animals,DocumentStorageService storage,AuditLogService audit){
        this.documents=documents;this.animals=animals;this.storage=storage;this.audit=audit;
    }

    @GetMapping
    public ResponseEntity<List<MedicalDocument>> list(@PathVariable UUID animalId){
        animals.findById(animalId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Animal not found"));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(documents.findByAnimalIdOrderByDocumentDateDescCreatedAtDescIdAsc(animalId, PageRequest.of(0, MAX_DOCUMENTS_PER_ANIMAL)));
    }


    @PostMapping(value="/upload",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public MedicalDocument upload(@PathVariable UUID animalId,@RequestPart("file") MultipartFile file,
                                  @RequestParam String documentType,@RequestParam String title,
                                  @RequestParam(required=false) String documentDate,
                                  @RequestParam(required=false) String notes,Authentication auth){
        var animal=animals.findById(animalId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Animal not found"));
        validateMetadata(documentType,title,notes);
        LocalDate parsedDocumentDate = parseDocumentDate(documentDate);
        try{
            var stored=storage.store(animalId,file);
            MedicalDocument doc;
            try{
                doc=new MedicalDocument();
                doc.setId(UUID.randomUUID());
                doc.setAnimal(animal);doc.setDocumentType(documentType);doc.setTitle(title);
                doc.setStorageKey(stored.storageKey());doc.setOriginalFileName(stored.originalFileName());
                doc.setContentType(stored.contentType());doc.setFileSize(stored.size());
                doc.setDocumentDate(parsedDocumentDate);
                doc.setNotes(notes);doc.setUploadedBy(auth.getName());
                doc.setFileUrl("/api/animals/"+animalId+"/documents/files/"+doc.getId());
                doc = documents.save(doc);
                audit.record(auth.getName(),"UPLOAD_MEDICAL_DOCUMENT","MEDICAL_DOCUMENT",doc.getId(),doc.getOriginalFileName());
            }catch(RuntimeException e){
                try{ storage.delete(stored.storageKey()); }catch(IOException cleanup){ e.addSuppressed(cleanup); }
                throw e;
            }
            return doc;
        }catch(IOException|IllegalArgumentException e){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid medical document upload");
        }
    }

    @GetMapping("/files/{documentId}")
    public ResponseEntity<PathResource> download(@PathVariable UUID animalId,@PathVariable UUID documentId, Authentication auth){
        var doc=documents.findById(documentId)
            .filter(d -> d.getAnimal().getId().equals(animalId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Document not found"));
        try{
            if(doc.getStorageKey()==null||doc.getStorageKey().isBlank())
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,"File not found");
            java.nio.file.Path resolved = storage.resolve(doc.getStorageKey());
            if(!java.nio.file.Files.isRegularFile(resolved, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                    || !java.nio.file.Files.isReadable(resolved)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"File not found");
            if(doc.getFileSize()!=null) {
                try {
                    if(java.nio.file.Files.size(resolved)!=doc.getFileSize())
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND,"File unavailable");
                } catch (java.io.IOException e) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND,"File unavailable",e);
                }
            }
            PathResource resource=new PathResource(resolved);
            MediaType type=MediaType.parseMediaType(doc.getContentType()==null?"application/octet-stream":doc.getContentType());
            ResponseEntity<PathResource> response = ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=\"" + safeDownloadFileName(doc.getOriginalFileName()) + "\"")
                .body(resource);
            audit.record(auth.getName(),"ACCESS_MEDICAL_DOCUMENT","MEDICAL_DOCUMENT",doc.getId(),doc.getOriginalFileName());
            return response;
        }catch(InvalidMediaTypeException e){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"File unavailable",e);
        }catch(IllegalArgumentException e){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"File unavailable",e);
        }
    }

    private void validateMetadata(String documentType,String title,String notes){
        if(documentType==null||!java.util.Set.of("LAB_RESULT","VET_REPORT","VACCINE_CERTIFICATE","OTHER").contains(documentType))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid document type");
        if(title==null||title.isBlank()||title.length()>200 || title.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>MAX_TITLE_UTF8_BYTES)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Title is required and must be at most 200 characters");
        if(notes!=null&&(notes.length()>10000 || notes.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>MAX_NOTES_UTF8_BYTES))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Notes must be at most 10000 characters");
    }

    private LocalDate parseDocumentDate(String value){
        if(value==null||value.isBlank()) return null;
        try{
            return LocalDate.parse(value);
        }catch(DateTimeParseException e){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Document date must be a valid ISO date (YYYY-MM-DD)",e);
        }
    }

    private String safeDownloadFileName(String name){
        if(name==null||name.isBlank()) return "document";
        String safe = name.replace("\\","_").replace("/","_").replaceAll("[\\r\\n\"]","_");
        StringBuilder result = new StringBuilder(Math.min(safe.length(), 255));
        for (int i = 0; i < safe.length() && result.length() < 255;) {
            int cp = safe.codePointAt(i);
            if (Character.isISOControl(cp)) cp = '_';
            result.appendCodePoint(cp);
            i += Character.charCount(safe.codePointAt(i));
        }
        return result.length() == 0 ? "document" : result.toString();
    }
}
