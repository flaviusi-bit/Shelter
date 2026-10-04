package com.shelter.api.medical;
import com.shelter.api.animal.AnimalRepository; import com.shelter.api.audit.AuditLogService; import com.shelter.api.task.TaskReminderService; import jakarta.validation.Valid; import jakarta.validation.constraints.*; import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException; import java.time.LocalDate; import java.time.OffsetDateTime; import java.util.*;
import org.springframework.data.domain.PageRequest; import org.springframework.security.core.Authentication;
@RestController @RequestMapping("/api/animals/{animalId}/vaccinations") public class VaccinationController {
 private static final int MAX_HISTORY_ENTRIES = 200;
 private final VaccinationRepository repo; private final AnimalRepository animals; private final TaskReminderService reminders; private final AuditLogService audit;
 public VaccinationController(VaccinationRepository r,AnimalRepository a,TaskReminderService s,AuditLogService audit){repo=r;animals=a;reminders=s;this.audit=audit;}
 @GetMapping public List<Response> list(@PathVariable UUID animalId){animals.findById(animalId).orElseThrow();return repo.findByAnimalIdOrderByAdministeredDateDesc(animalId, PageRequest.of(0, MAX_HISTORY_ENTRIES)).stream().map(v -> toResponse(v, reminders.reminderStatus("VACCINATION_DUE:" + v.getId()))).toList();}
 @PostMapping @Transactional public Response create(@PathVariable UUID animalId,@Valid @RequestBody Request r,Authentication auth){
        validateUtf8(r.vaccineName(), 640, "vaccineName");
        validateUtf8(r.vaccineType(), 400, "vaccineType");
        validateUtf8(r.batchNumber(), 400, "batchNumber");
        validateUtf8(r.veterinarian(), 640, "veterinarian");
        validateUtf8(r.notes(), 40000, "notes");
if(r.nextDueDate()!=null&&r.nextDueDate().isBefore(r.administeredDate()))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Next due date cannot be before administered date"); Vaccination v=new Vaccination();v.setAnimal(animals.findById(animalId).orElseThrow());v.setVaccineName(r.vaccineName());v.setVaccineType(r.vaccineType());v.setAdministeredDate(r.administeredDate());v.setNextDueDate(r.nextDueDate());v.setBatchNumber(r.batchNumber());v.setVeterinarian(r.veterinarian());v.setNotes(r.notes());v.setCreatedBy(auth.getName());v=repo.save(v);reminders.syncVaccination(v);audit.record(auth.getName(),"CREATE_VACCINATION","VACCINATION",v.getId(),r.vaccineName());return toResponse(v, reminders.reminderStatus("VACCINATION_DUE:" + v.getId()));}
 @PutMapping("/{vaccinationId}") @Transactional public Response update(@PathVariable UUID animalId,@PathVariable UUID vaccinationId,@Valid @RequestBody Request r,Authentication auth){
        Vaccination v=repo.findById(vaccinationId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Vaccination not found"));
        if(!v.getAnimal().getId().equals(animalId))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Vaccination not found");
        validateRequest(r);
        v.setVaccineName(r.vaccineName());v.setVaccineType(r.vaccineType());v.setAdministeredDate(r.administeredDate());v.setNextDueDate(r.nextDueDate());v.setBatchNumber(r.batchNumber());v.setVeterinarian(r.veterinarian());v.setNotes(r.notes());
        v=repo.save(v);reminders.syncVaccination(v);audit.record(auth.getName(),"UPDATE_VACCINATION","VACCINATION",v.getId(),r.vaccineName());return toResponse(v, reminders.reminderStatus("VACCINATION_DUE:" + v.getId()));
 }
 @PostMapping("/{vaccinationId}/complete") @Transactional public Response complete(@PathVariable UUID animalId,@PathVariable UUID vaccinationId,Authentication auth){
        Vaccination x=repo.findById(vaccinationId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Vaccination not found"));
        if(!x.getAnimal().getId().equals(animalId))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Vaccination not found");
        reminders.completeReminder("VACCINATION_DUE:" + x.getId(), animalId, auth.getName());
        return toResponse(x, reminders.reminderStatus("VACCINATION_DUE:" + x.getId()));
 }
 @DeleteMapping("/${idName}") @Transactional public void delete(@PathVariable UUID animalId,@PathVariable UUID vaccinationId,Authentication auth){
        Vaccination v=repo.findById(vaccinationId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Vaccination not found"));
        if(!v.getAnimal().getId().equals(animalId))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Vaccination not found");
        reminders.removeVaccinationReminder(v);repo.delete(v);audit.record(auth.getName(),"DELETE_VACCINATION","VACCINATION",v.getId(),v.getVaccineName());
 }
 private void validateRequest(Request r){
        validateUtf8(r.vaccineName(),640,"vaccineName");validateUtf8(r.vaccineType(),400,"vaccineType");validateUtf8(r.batchNumber(),400,"batchNumber");validateUtf8(r.veterinarian(),640,"veterinarian");validateUtf8(r.notes(),40000,"notes");
        if(r.nextDueDate()!=null&&r.nextDueDate().isBefore(r.administeredDate()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Next due date cannot be before administered date");
 }
 private static Response toResponse(Vaccination v,String reminderStatus){return new Response(v.getId(),v.getVaccineName(),v.getVaccineType(),v.getAdministeredDate(),v.getNextDueDate(),v.getBatchNumber(),v.getVeterinarian(),v.getNotes(),reminderStatus);}
 private static void validateUtf8(String value,int maxBytes,String field){if(value!=null&&value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>maxBytes)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,field+" is too long");}
 public record Request(@NotBlank @Size(max=160) String vaccineName,@Size(max=100) String vaccineType,@NotNull LocalDate administeredDate,LocalDate nextDueDate,@Size(max=100) String batchNumber,@Size(max=160) String veterinarian,@Size(max=10000) String notes){}
 public record Response(UUID id,String vaccineName,String vaccineType,LocalDate administeredDate,LocalDate nextDueDate,String batchNumber,String veterinarian,String notes,String reminderStatus){}
}
