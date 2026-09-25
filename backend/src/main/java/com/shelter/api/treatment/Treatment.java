package com.shelter.api.treatment;

import com.shelter.api.animal.Animal;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name="treatments")
public class Treatment {
 @Id private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="animal_id") private Animal animal;
 @Column(nullable=false,length=160) private String medication;
 @Column(nullable=false,length=80) private String dose;
 @Column(nullable=false,length=40) private String route;
 @Column(nullable=false,length=80) private String frequency;
 @Column(nullable=false) private LocalDate startDate;
 private LocalDate endDate;
 @Column(length=30,nullable=false) private String status="ACTIVE";
 @Column(columnDefinition="TEXT") private String instructions;
 @Column(length=120) private String prescribedBy;

 @PrePersist void init(){if(id==null)id=UUID.randomUUID();}
 public UUID getId(){return id;} public Animal getAnimal(){return animal;} public void setAnimal(Animal v){animal=v;}
 public String getMedication(){return medication;} public void setMedication(String v){medication=v;}
 public String getDose(){return dose;} public void setDose(String v){dose=v;}
 public String getRoute(){return route;} public void setRoute(String v){route=v;}
 public String getFrequency(){return frequency;} public void setFrequency(String v){frequency=v;}
 public LocalDate getStartDate(){return startDate;} public void setStartDate(LocalDate v){startDate=v;}
 public LocalDate getEndDate(){return endDate;} public void setEndDate(LocalDate v){endDate=v;}
 public String getStatus(){return status;} public void setStatus(String v){status=v;}
 public String getInstructions(){return instructions;} public void setInstructions(String v){instructions=v;}
 public String getPrescribedBy(){return prescribedBy;} public void setPrescribedBy(String v){prescribedBy=v;}
}