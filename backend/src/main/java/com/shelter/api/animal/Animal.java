package com.shelter.api.animal;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "animals")
public class Animal {
    @Id private UUID id;
    @Column(nullable=false,length=120) private String name;
    @Column(name="animal_type",nullable=false,length=30) private String animalType;
    @Column(nullable=false,length=20) private String sex;
    private LocalDate dateOfBirth;
    @Column(precision=7,scale=3) private BigDecimal weightKg;
    @Column(length=80) private String microchipNumber;
    @Column(nullable=false) private LocalDate intakeDate;
    private String rescueSource;
    @Column(length=120) private String location;
    @Column(nullable=false,length=30) private String status="ACTIVE";
    @Column(columnDefinition="TEXT") private String notes;
    @Column(nullable=false) private OffsetDateTime createdAt;
    @Column(nullable=false) private OffsetDateTime updatedAt;

    @PrePersist void onCreate(){ if(id==null) id=UUID.randomUUID(); var now=OffsetDateTime.now(); if(createdAt==null)createdAt=now; updatedAt=now; }
    @PreUpdate void onUpdate(){updatedAt=OffsetDateTime.now();}

    public UUID getId(){return id;} public void setId(UUID v){id=v;}
    public String getName(){return name;} public void setName(String v){name=v;}
    public String getAnimalType(){return animalType;} public void setAnimalType(String v){animalType=v;}
    public String getSex(){return sex;} public void setSex(String v){sex=v;}
    public LocalDate getDateOfBirth(){return dateOfBirth;} public void setDateOfBirth(LocalDate v){dateOfBirth=v;}
    public BigDecimal getWeightKg(){return weightKg;} public void setWeightKg(BigDecimal v){weightKg=v;}
    public String getMicrochipNumber(){return microchipNumber;} public void setMicrochipNumber(String v){microchipNumber=v;}
    public LocalDate getIntakeDate(){return intakeDate;} public void setIntakeDate(LocalDate v){intakeDate=v;}
    public String getRescueSource(){return rescueSource;} public void setRescueSource(String v){rescueSource=v;}
    public String getLocation(){return location;} public void setLocation(String v){location=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getNotes(){return notes;} public void setNotes(String v){notes=v;}
    public OffsetDateTime getCreatedAt(){return createdAt;} public OffsetDateTime getUpdatedAt(){return updatedAt;}
}