package com.shelter.api.medical;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List; import java.util.UUID;
public interface MedicalEventRepository extends JpaRepository<MedicalEvent,UUID>{List<MedicalEvent> findByAnimalIdOrderByEventDateDesc(UUID animalId);}