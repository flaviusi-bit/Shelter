package com.shelter.api.medical;
import com.shelter.api.animal.AnimalRepository; import com.shelter.api.audit.AuditLogService;
import jakarta.validation.Valid; import jakarta.validation.constraints.*; import org.springframework.web.bind.annotation.*; import java.time.LocalDate; import java.util.List; import java.util.UUID;
@RestController @RequestMapping("/api/animals/{animalId}/medical-events")
public class MedicalEventController {
 private final MedicalEventRepository repo; private final AnimalRepository animals; private final AuditLogService audit;
 public MedicalEventController(MedicalEventRepository r,AnimalRepository a,AuditLogService audit){repo=r;animals=a;this.audit=audit;}
 @GetMapping public List<MedicalEvent> list(@PathVariable UUID animalId){animals.findById(animalId).orElseThrow();return repo.findByAnimalIdOrderByEventDateDesc(animalId);}
 @PostMapping public MedicalEvent create(@PathVariable UUID animalId,@Valid @RequestBody Request r,org.springframework.security.core.Authentication auth){
  MedicalEvent e=new MedicalEvent();e.setAnimal(animals.findById(animalId).orElseThrow());e.setEventType(r.eventType());e.setEventDate(r.eventDate());e.setTitle(r.title());e.setDiagnosis(r.diagnosis());e.setProvider(r.provider());e.setNotes(r.notes());e.setCreatedBy(auth.getName());e=repo.save(e);
  audit.record(auth.getName(),"CREATE_MEDICAL_EVENT","MEDICAL_EVENT",e.getId(),r.title()); return e;}
 public record Request(@NotBlank @Size(max=30) String eventType,@NotNull LocalDate eventDate,@NotBlank @Size(max=200) String title,@Size(max=255) String diagnosis,@Size(max=160) String provider,@Size(max=10000) String notes){}
}