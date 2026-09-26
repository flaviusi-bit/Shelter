package com.shelter.api.document;

import com.shelter.api.animal.AnimalRepository;
import com.shelter.api.audit.AuditLogService;
import org.springframework.core.io.PathResource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/animals/{animalId}/documents")
public class MedicalDocumentController {
    private final MedicalDocumentRepository documents;
    private final AnimalRepository animals;
    private final DocumentStorageService storage;
    private final AuditLogService audit;

    public MedicalDocumentController(MedicalDocumentRepository documents,AnimalRepository animals,DocumentStorageService storage,AuditLogService audit){
        this.documents=documents;this.animals=animals;this.storage=storage;this.audit=audit;
    }

    @GetMapping
    public List<MedicalDocument> list(@PathVariable UUID animalId){
        animals.findById(animalId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Animal not found"));
        return documents.findByAnimalIdOrderByDocumentDateDescCreatedAtDesc(animalId);
    }

    @PostMapping
    public MedicalDocument create(@PathVariable UUID animalId,@RequestBody MedicalDocument input,Authentication auth){
        var animal=animals.findById(animalId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Animal not found"));
        validateMetadata(input.getDocumentType(),input.getTitle(),input.getNotes());
        if(input.getFileUrl()==null||input.getFileUrl().isBlank()||input.getFileUrl().length()>1000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"File URL is required and must be at most 1000 characters");
        input.setAnimal(animal); input.setUploadedBy(auth.getName());
        var saved=documents.save(input);
        audit.record(auth.getName(),"CREATE_MEDICAL_DOCUMENT","MEDICAL_DOCUMENT",saved.getId(),saved.getTitle());
        return saved;
    }

    @PostMapping(value="/upload",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public MedicalDocument upload(@PathVariable UUID animalId,@RequestPart("file") MultipartFile file,
                                  @RequestParam String documentType,@RequestParam String title,
                                  @RequestParam(required=false) String documentDate,
                                  @RequestParam(required=false) String notes,Authentication auth){
        var animal=animals.findById(animalId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Animal not found"));
        validateMetadata(documentType,title,notes);
        LocalDate parsedDocumentDate = parseDocumentDate(documentDate);
        try{
            var stored=storage.store(animalId,file);
            try{
                var doc=new MedicalDocument();
                doc.setAnimal(animal);doc.setDocumentType(documentType);doc.setTitle(title);
                doc.setStorageKey(stored.storageKey());doc.setOriginalFileName(stored.originalFileName());
                doc.setContentType(stored.contentType());doc.setFileSize(stored.size());
                doc.setDocumentDate(parsedDocumentDate);
                doc.setNotes(notes);doc.setUploadedBy(auth.getName());
                doc = documents.save(doc);
                doc.setFileUrl("/api/animals/"+animalId+"/documents/files/"+doc.getId());
                doc = documents.save(doc);
                audit.record(auth.getName(),"UPLOAD_MEDICAL_DOCUMENT","MEDICAL_DOCUMENT",doc.getId(),doc.getOriginalFileName());
                return doc;
            }catch(RuntimeException e){
                try{ storage.delete(stored.storageKey()); }catch(IOException cleanup){ e.addSuppressed(cleanup); }
                throw e;
            }
        }catch(IOException|IllegalArgumentException e){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getMessage(),e);
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
            PathResource resource=new PathResource(storage.resolve(doc.getStorageKey()));
            if(!resource.exists()||!resource.isReadable()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"File not found");
            audit.record(auth.getName(),"ACCESS_MEDICAL_DOCUMENT","MEDICAL_DOCUMENT",doc.getId(),doc.getOriginalFileName());
            MediaType type=MediaType.parseMediaType(doc.getContentType()==null?"application/octet-stream":doc.getContentType());
            return ResponseEntity.ok().contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=\"" + safeDownloadFileName(doc.getOriginalFileName()) + "\"")
                .body(resource);
        }catch(InvalidMediaTypeException e){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"File unavailable",e);
        }catch(IllegalArgumentException e){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"File unavailable",e);
        }
    }

    private void validateMetadata(String documentType,String title,String notes){
        if(documentType==null||documentType.isBlank()||documentType.length()>50)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Document type is required and must be at most 50 characters");
        if(title==null||title.isBlank()||title.length()>200)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Title is required and must be at most 200 characters");
        if(notes!=null&&notes.length()>10000)
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
        return name.replace("\\","_").replaceAll("[\\r\\n\"]","_");
    }
}
