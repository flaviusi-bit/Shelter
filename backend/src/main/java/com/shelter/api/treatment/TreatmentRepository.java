package com.shelter.api.treatment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface TreatmentRepository extends JpaRepository<Treatment,UUID>{
 List<Treatment> findByAnimalIdOrderByStartDateDesc(UUID animalId, Pageable pageable);
 List<Treatment> findByStatusIgnoreCase(String status);
}