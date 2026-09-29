package com.shelter.api.medical;
import com.shelter.api.animal.AnimalRepository; import com.shelter.api.audit.AuditLogService; import com.shelter.api.task.TaskReminderService; import jakarta.validation.Valid; import jakarta.validation.constraints.*; import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException; import java.time.LocalDate; import java.util.*;
import org.springframework.data.domain.PageRequest; import org.springframework.security.core.Authentication;
@RestController @RequestMapping("/api/animals/{animalId}/dewormings") public class DewormingController {
 private static final int MAX_HISTORY_ENTRIES = 200;
 private final DewormingRepository repo; private final AnimalRepository animals; private final TaskReminderService reminders; private final AuditLogService audit;
 public DewormingController(DewormingRepository r,AnimalRepository a,TaskReminderService s,AuditLogService audit){repo=r;animals=a;reminders=s;this.audit=audit;}
 @GetMapping public List<Deworming> list(@PathVariable UUID animalId){animals.findById(animalId).orElseThrow();return repo.findByAnimalIdOrderByAdministeredDateDesc(animalId, PageRequest.of(0, MAX_HISTORY_ENTRIES));}
 @PostMapping public Deworming create(@PathVariable UUID animalId,@Valid @RequestBody Request r,Authentication auth){
        validateUtf8(r.productName(), 640, "productName");
        validateUtf8(r.treatmentType(), 400, "treatmentType");
        validateUtf8(r.dose(), 400, "dose");
        validateUtf8(r.veterinarian(), 640, "veterinarian");
        validateUtf8(r.notes(), 40000, "notes");
if(r.nextDueDate()!=null&&r.nextDueDate().isBefore(r.administeredDate()))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Next due date cannot be before administered date"); Deworming d=new Deworming();d.setAnimal(animals.findById(animalId).orElseThrow());d.setProductName(r.productName());d.setTreatmentType(r.treatmentType());d.setAdministeredDate(r.administeredDate());d.setNextDueDate(r.nextDueDate());d.setDose(r.dose());d.setVeterinarian(r.veterinarian());d.setNotes(r.notes());d.setCreatedBy(auth.getName());d=repo.save(d);reminders.syncDeworming(d);audit.record(auth.getName(),"CREATE_DEWORMING","DEWORMING",d.getId(),r.productName());return d;}
 private static void validateUtf8(String value,int maxBytes,String field){if(value!=null&&value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>maxBytes)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,field+" is too long");}
 public record Request(@NotBlank @Size(max=160) String productName,@Size(max=100) String treatmentType,@NotNull LocalDate administeredDate,LocalDate nextDueDate,@Size(max=100) String dose,@Size(max=160) String veterinarian,@Size(max=10000) String notes){}
}