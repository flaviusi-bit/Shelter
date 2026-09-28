package com.shelter.api.medical; import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
 import java.util.*; public interface DewormingRepository extends JpaRepository<Deworming,UUID>{List<Deworming> findByAnimalIdOrderByAdministeredDateDesc(UUID animalId, Pageable pageable);}