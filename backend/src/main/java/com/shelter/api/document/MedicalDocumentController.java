package com.shelter.api.document;

import com.shelter.api.animal.AnimalRepository;
import org.springframework.core.io.PathResource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/animals/{animalId}/documents")
public class MedicalDocumentController {
    private final MedicalDocumentRepository documents;
    private final AnimalRepository animals;
    private final DocumentStorageService storage;

    public MedicalDocumentController(MedicalDocumentRepository documents,AnimalRepository animals,DocumentStorageService storage){
        this.documents=documents;this.animals=animals;this.storage=storage;
    }

    @GetMapping
    public List<MedicalDocument> list(@PathVariable UUID animalId){
        animals.findById(animalId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Animal not found"));
        return documents.findByAnimalIdOrderByDocumentDateDescCreatedAtDesc(animalId);
    }

    @PostMapping
    public MedicalDocument create(@PathVariable UUID animalId,@RequestBody MedicalDocument input,Authentication auth){
        var animal=animals.findById(animalId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Animal not found"));
        input.setAnimal(animal); input.setUploadedBy(auth.getName()); input.setId(null);
        return documents.save(input);
    }

    @PostMapping(value="/upload",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public MedicalDocument upload(@PathVariable UUID animalId,@RequestPart("file") MultipartFile file,
                                  @RequestParam String documentType,@RequestParam String title,
                                  @RequestParam(required=false) String documentDate,
                                  @RequestParam(required=false) String notes,Authentication auth){
        var animal=animals.findById(animalId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Animal not found"));
        try{
            var stored=storage.store(animalId,file);
            var doc=new MedicalDocument();
            doc.setAnimal(animal);doc.setDocumentType(documentType);doc.setTitle(title);
            doc.setFileUrl("/api/animals/"+animalId+"/documents/files/"+doc.getId());
            doc.setStorageKey(stored.storageKey());doc.setOriginalFileName(stored.originalFileName());
            doc.setContentType(stored.contentType());doc.setFileSize(stored.size());
            if(documentDate!=null&&!documentDate.isBlank()) doc.setDocumentDate(java.time.LocalDate.parse(documentDate));
            doc.setNotes(notes);doc.setUploadedBy(auth.getName());
            return documents.save(doc);
        }catch(IOException|IllegalArgumentException e){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getMessage(),e);
        }
    }

    @GetMapping("/files/{documentId}")
    public ResponseEntity<PathResource> download(@PathVariable UUID animalId,@PathVariable UUID documentId){
        var doc=documents.findById(documentId)
            .filter(d -> d.getAnimal().getId().equals(animalId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Document not found"));
        try{
            PathResource resource=new PathResource(storage.resolve(doc.getStorageKey()));
            if(!resource.exists()||!resource.isReadable()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"File not found");
            MediaType type=MediaType.parseMediaType(doc.getContentType()==null?"application/octet-stream":doc.getContentType());
            return ResponseEntity.ok().contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=\"" + doc.getOriginalFileName() + "\"")
                .body(resource);
        }catch(IOException|InvalidMediaTypeException e){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"File unavailable",e);
        }
    }
}
