package com.shelter.api.medical;
import com.shelter.api.animal.AnimalRepository; import com.shelter.api.audit.AuditLogService;
import jakarta.validation.Valid; import jakarta.validation.constraints.*; import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException; import java.time.LocalDate; import java.util.List;
import org.springframework.data.domain.PageRequest; import java.util.UUID;
@RestController @RequestMapping("/api/animals/{animalId}/medical-events")
public class MedicalEventController {
 private static final int MAX_HISTORY_ENTRIES = 200;
 private final MedicalEventRepository repo; private final AnimalRepository animals; private final AuditLogService audit;
 public MedicalEventController(MedicalEventRepository r,AnimalRepository a,AuditLogService audit){repo=r;animals=a;this.audit=audit;}
 @GetMapping public List<MedicalEvent> list(@PathVariable UUID animalId){animals.findById(animalId).orElseThrow();return repo.findByAnimalIdOrderByEventDateDescIdAsc(animalId, PageRequest.of(0, MAX_HISTORY_ENTRIES));}
 @PostMapping @Transactional public MedicalEvent create(@PathVariable UUID animalId,@Valid @RequestBody Request r,org.springframework.security.core.Authentication auth){
        validateRequest(r);
  MedicalEvent e=new MedicalEvent();e.setAnimal(animals.findById(animalId).orElseThrow());apply(e,r);e.setCreatedBy(auth.getName());e=repo.save(e);
  audit.record(auth.getName(),"CREATE_MEDICAL_EVENT","MEDICAL_EVENT",e.getId(),r.title()); return e;}

 @PutMapping("/{eventId}") @Transactional public MedicalEvent update(@PathVariable UUID animalId,@PathVariable UUID eventId,@Valid @RequestBody Request r,org.springframework.security.core.Authentication auth){
        validateRequest(r);
        MedicalEvent e=repo.findById(eventId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Medical event not found"));
        if(!e.getAnimal().getId().equals(animalId))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Medical event not found");
        apply(e,r);e=repo.save(e);
        audit.record(auth.getName(),"UPDATE_MEDICAL_EVENT","MEDICAL_EVENT",e.getId(),r.title()); return e;
 }
 @DeleteMapping("/{eventId}") @Transactional public void delete(@PathVariable UUID animalId,@PathVariable UUID eventId,org.springframework.security.core.Authentication auth){
        MedicalEvent e=repo.findById(eventId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Medical event not found"));
        if(!e.getAnimal().getId().equals(animalId))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Medical event not found");
        repo.delete(e);audit.record(auth.getName(),"DELETE_MEDICAL_EVENT","MEDICAL_EVENT",e.getId(),e.getTitle());
 }
 private static void apply(MedicalEvent e,Request r){
  e.setEventType(r.eventType());e.setEventDate(r.eventDate());e.setTitle(r.title());e.setDiagnosis(r.diagnosis());e.setProvider(r.provider());e.setNotes(r.notes());
 }
 private static void validateRequest(Request r){
        validateUtf8(r.eventType(), 160, "eventType");
        validateUtf8(r.title(), 800, "title");
        validateUtf8(r.diagnosis(), 1020, "diagnosis");
        validateUtf8(r.provider(), 640, "provider");
        validateUtf8(r.notes(), 40000, "notes");
 }
 private static void validateUtf8(String value,int maxBytes,String field){if(value!=null&&value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>maxBytes)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,field+" is too long");}
 public record Request(@NotBlank @Pattern(regexp="VET_VISIT|DIAGNOSIS|LAB_RESULT|OTHER") String eventType,@NotNull LocalDate eventDate,@NotBlank @Size(max=200) String title,@Size(max=255) String diagnosis,@Size(max=160) String provider,@Size(max=10000) String notes){}
}