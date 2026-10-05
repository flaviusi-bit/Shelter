package com.shelter.api.animal;

import com.shelter.api.audit.AuditLogService;
import com.shelter.api.document.DocumentStorageService;
import com.shelter.api.document.MedicalDocumentRepository;
import org.springframework.core.io.PathResource;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import java.util.UUID;

@RestController
@RequestMapping("/api/animals")
public class AnimalController {
    private final AnimalRepository repository;
    private final AuditLogService audit;
    private final DocumentStorageService storage;
    private final MedicalDocumentRepository documents;

    public AnimalController(AnimalRepository repository, AuditLogService audit, DocumentStorageService storage, MedicalDocumentRepository documents){
        this.repository=repository;this.audit=audit;this.storage=storage;this.documents=documents;
    }

    @GetMapping public List<Animal> list(@RequestParam(required=false) String q){
        if(q==null||q.isBlank()) return repository.findAll(PageRequest.of(0, 200, org.springframework.data.domain.Sort.by("name").ascending())).getContent();
        if(q.length()>100) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,"Search query is too long");
        return repository.findByNameContainingIgnoreCaseOrMicrochipNumberContainingIgnoreCaseOrAnimalCodeContainingIgnoreCaseOrderByNameAsc(q,q,q, PageRequest.of(0, 200));
    }

    @GetMapping("/{id}") public Animal get(@PathVariable UUID id){
        return repository.findById(id).orElseThrow(()->new AnimalNotFoundException(id));
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Animal create(@Valid @RequestBody AnimalRequest r, Authentication auth){
        validateDates(r); Animal a=new Animal(); apply(a,r); var saved=repository.save(a);
        audit.record(auth.getName(),"CREATE_ANIMAL","ANIMAL",saved.getId(),saved.getName()); return saved;
    }

    @PutMapping("/{id}") @Transactional
    public Animal update(@PathVariable UUID id,@Valid @RequestBody AnimalRequest r, Authentication auth){
        validateDates(r); Animal a=repository.findById(id).orElseThrow(()->new AnimalNotFoundException(id));
        apply(a,r); var saved=repository.save(a); audit.record(auth.getName(),"UPDATE_ANIMAL","ANIMAL",saved.getId(),saved.getName()); return saved;
    }

    @DeleteMapping("/{id}") @Transactional
    public void delete(@PathVariable UUID id, Authentication auth){
        Animal a=repository.findById(id).orElseThrow(()->new AnimalNotFoundException(id));
        try{
            if(a.getPhotoStorageKey()!=null&&!a.getPhotoStorageKey().isBlank()) storage.delete(a.getPhotoStorageKey());
            for(var document: documents.findByAnimalId(id)){
                if(document.getStorageKey()!=null&&!document.getStorageKey().isBlank()) storage.delete(document.getStorageKey());
            }
        }catch(IOException|IllegalArgumentException e){
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"Could not remove animal files",e);
        }
        repository.delete(a);
        audit.record(auth.getName(),"DELETE_ANIMAL","ANIMAL",id,a.getName());
    }

    @PostMapping(value="/{id}/photo", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public Animal uploadPhoto(@PathVariable UUID id,@RequestPart("file") MultipartFile file,Authentication auth){
        Animal animal=repository.findById(id).orElseThrow(()->new AnimalNotFoundException(id));
        String oldKey=animal.getPhotoStorageKey();
        try{
            var stored=storage.store(id,file);
            animal.setPhotoStorageKey(stored.storageKey());
            animal.setPhotoUrl("/api/animals/"+id+"/photo");
            var saved=repository.save(animal);
            if(oldKey!=null&&!oldKey.isBlank()&&!oldKey.equals(stored.storageKey())){
                try{storage.delete(oldKey);}catch(IOException ignored){}
            }
            audit.record(auth.getName(),"UPLOAD_ANIMAL_PHOTO","ANIMAL",id,stored.originalFileName());
            return saved;
        }catch(IOException|IllegalArgumentException e){
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid animal photo upload");
        }
    }

    @GetMapping("/{id}/photo")
    public ResponseEntity<PathResource> photo(@PathVariable UUID id){
        Animal animal=repository.findById(id).orElseThrow(()->new AnimalNotFoundException(id));
        String key=animal.getPhotoStorageKey();
        if(key==null||key.isBlank()) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND,"Animal photo not found");
        try{
            var path=storage.resolve(key);
            if(!java.nio.file.Files.isRegularFile(path,java.nio.file.LinkOption.NOFOLLOW_LINKS)
                    ||!java.nio.file.Files.isReadable(path)) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND,"Animal photo not found");
            MediaType type=photoMediaType(path.getFileName().toString());
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(type).body(new PathResource(path));
        }catch(org.springframework.web.server.ResponseStatusException e){throw e;}
        catch(IllegalArgumentException e){
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND,"Animal photo not found");
        }
    }

    private MediaType photoMediaType(String name){
        String lower=name.toLowerCase(java.util.Locale.ROOT);
        if(lower.endsWith(".jpg")||lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if(lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if(lower.endsWith(".gif")) return MediaType.IMAGE_GIF;
        if(lower.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        if(lower.endsWith(".bmp")) return MediaType.parseMediaType("image/bmp");
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private void validateDates(AnimalRequest r){
        if(r.dateOfBirth()!=null && r.dateOfBirth().isAfter(LocalDate.now()))
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,"Date of birth cannot be in the future");
        if(r.dateOfBirth()!=null && r.dateOfBirth().isAfter(r.intakeDate()))
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,"Date of birth cannot be after intake date");
    }

    private void apply(Animal a,AnimalRequest r){
        a.setName(r.name()); a.setAnimalType(r.animalType()); a.setSex(r.sex()); a.setDateOfBirth(r.dateOfBirth());
        a.setWeightKg(r.weightKg()); a.setMicrochipNumber(r.microchipNumber()); a.setIntakeDate(r.intakeDate());
        a.setRescueSource(r.rescueSource()); a.setLocation(r.location()); a.setStatus(r.status()==null||r.status().isBlank()?"ACTIVE":r.status());
        a.setNotes(r.notes());
        if(r.photoUrl()!=null) a.setPhotoUrl(r.photoUrl());
    }

    public record AnimalRequest(
        @NotBlank @Size(max=120) String name,@NotBlank @Pattern(regexp="DOG|CAT|OTHER") String animalType,@NotBlank @Pattern(regexp="UNKNOWN|FEMALE|MALE") String sex,
        LocalDate dateOfBirth,@DecimalMin("0.001") @DecimalMax("9999.999") @Digits(integer=4,fraction=3) BigDecimal weightKg,@Size(max=80) String microchipNumber,
        @NotNull LocalDate intakeDate,@Size(max=255) String rescueSource,@Size(max=120) String location,
        @Pattern(regexp="ACTIVE|TREATMENT|HEALTHY|QUARANTINE|FOSTER|ADOPTED") String status,@Size(max=10000) String notes,@Size(max=1000) String photoUrl){}

    @ResponseStatus(HttpStatus.NOT_FOUND)
    static class AnimalNotFoundException extends RuntimeException{AnimalNotFoundException(UUID id){super("Animal not found: "+id);}}
}
