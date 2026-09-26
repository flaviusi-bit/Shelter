package com.shelter.api.animal;

import com.shelter.api.audit.AuditLogService;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/animals")
public class AnimalController {
    private final AnimalRepository repository;
    private final AuditLogService audit;
    public AnimalController(AnimalRepository repository, AuditLogService audit){this.repository=repository;this.audit=audit;}

    @GetMapping public List<Animal> list(@RequestParam(required=false) String q){
        if(q==null||q.isBlank()) return repository.findAll(org.springframework.data.domain.Sort.by("name").ascending());
        return repository.findByNameContainingIgnoreCaseOrMicrochipNumberContainingIgnoreCaseOrAnimalCodeContainingIgnoreCaseOrderByNameAsc(q,q,q);
    }
    @GetMapping("/{id}") public Animal get(@PathVariable UUID id){ return repository.findById(id).orElseThrow(()->new AnimalNotFoundException(id)); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Animal create(@Valid @RequestBody AnimalRequest r, Authentication auth){ Animal a=new Animal(); apply(a,r); var saved=repository.save(a); audit.record(auth.getName(),"CREATE_ANIMAL","ANIMAL",saved.getId(),saved.getName()); return saved; }
    @PutMapping("/{id}") public Animal update(@PathVariable UUID id,@Valid @RequestBody AnimalRequest r, Authentication auth){ Animal a=repository.findById(id).orElseThrow(()->new AnimalNotFoundException(id)); apply(a,r); var saved=repository.save(a); audit.record(auth.getName(),"UPDATE_ANIMAL","ANIMAL",saved.getId(),saved.getName()); return saved; }
    private void apply(Animal a,AnimalRequest r){
        a.setName(r.name()); a.setAnimalType(r.animalType()); a.setSex(r.sex()); a.setDateOfBirth(r.dateOfBirth());
        a.setWeightKg(r.weightKg()); a.setMicrochipNumber(r.microchipNumber()); a.setIntakeDate(r.intakeDate());
        a.setRescueSource(r.rescueSource()); a.setLocation(r.location()); a.setStatus(r.status()==null||r.status().isBlank()?"ACTIVE":r.status());
        a.setNotes(r.notes()); a.setPhotoUrl(r.photoUrl());
    }
    public record AnimalRequest(
        @NotBlank @Size(max=120) String name,@NotBlank @Size(max=30) String animalType,@NotBlank @Size(max=20) String sex,
        LocalDate dateOfBirth,@DecimalMin("0.001") BigDecimal weightKg,@Size(max=80) String microchipNumber,
        @NotNull LocalDate intakeDate,@Size(max=255) String rescueSource,@Size(max=120) String location,
        @Size(max=30) String status,@Size(max=10000) String notes,@Size(max=1000) String photoUrl){}
    @ResponseStatus(HttpStatus.NOT_FOUND)
    static class AnimalNotFoundException extends RuntimeException{AnimalNotFoundException(UUID id){super("Animal not found: "+id);}}
}