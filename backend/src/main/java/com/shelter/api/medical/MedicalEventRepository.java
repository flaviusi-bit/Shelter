package com.shelter.api.medical;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.util.List; import java.util.UUID;
public interface MedicalEventRepository extends JpaRepository<MedicalEvent,UUID>{List<MedicalEvent> findByAnimalIdOrderByEventDateDescIdAsc(UUID animalId, Pageable pageable);}