package com.shelter.api.treatment;

import com.shelter.api.animal.AnimalRepository;
import com.shelter.api.audit.AuditLogService;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/animals/{animalId}/treatments")
public class TreatmentController {
 private final TreatmentRepository treatments; private final AnimalRepository animals; private final AuditLogService audit;
 public TreatmentController(TreatmentRepository treatments,AnimalRepository animals,AuditLogService audit){this.treatments=treatments;this.animals=animals;this.audit=audit;}
 @GetMapping public List<Treatment> list(@PathVariable UUID animalId){ensureAnimal(animalId);return treatments.findByAnimalIdOrderByStartDateDesc(animalId);}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public Treatment create(@PathVariable UUID animalId,@Valid @RequestBody Request r,Authentication auth){
  var animal=ensureAnimal(animalId); validateFrequency(r.frequency()); if (r.endDate()!=null && r.endDate().isBefore(r.startDate())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Treatment end date cannot be before start date"); var t=new Treatment(); t.setAnimal(animal); t.setMedication(r.medication()); t.setDose(r.dose()); t.setRoute(r.route()); t.setFrequency(r.frequency()); t.setStartDate(r.startDate()); t.setEndDate(r.endDate()); t.setStatus(r.status()==null?"ACTIVE":r.status()); t.setInstructions(r.instructions()); t.setPrescribedBy(r.prescribedBy()); var saved=treatments.save(t); audit.record(auth.getName(),"CREATE_TREATMENT","TREATMENT",saved.getId(),saved.getMedication()); return saved;
 }
 private void validateFrequency(String frequency){
  String f=frequency.trim().toLowerCase();
  if(f.matches("once|single|daily|once daily|q24h|every 24 hours?|twice daily|two times daily|q12h|every 12 hours?|three times daily|q8h|every 8 hours?|four times daily|q6h|every 6 hours?")) return;
  var hours=java.util.regex.Pattern.compile("every\\s+([1-9]\\d*)\\s+hours?").matcher(f);
  if(hours.matches()){
   long value;
   try{value=Long.parseLong(hours.group(1));}catch(NumberFormatException ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid frequency interval");}
   if(value<=24L*365L) return;
  }
  var days=java.util.regex.Pattern.compile("every\\s+([1-9]\\d*)\\s+days?").matcher(f);
  if(days.matches()){
   long value;
   try{value=Long.parseLong(days.group(1));}catch(NumberFormatException ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid frequency interval");}
   if(value<=365L) return;
  }
  throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported treatment frequency");
 }
 private com.shelter.api.animal.Animal ensureAnimal(UUID id){return animals.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Animal not found"));}
 public record Request(@NotBlank @Size(max=160) String medication,@NotBlank @Size(max=80) String dose,@NotBlank @Size(max=40) String route,@NotBlank @Size(max=80) String frequency,@NotNull LocalDate startDate,LocalDate endDate,@Pattern(regexp="ACTIVE|COMPLETED|CANCELLED") String status,@Size(max=10000) String instructions,@Size(max=120) String prescribedBy){}
}