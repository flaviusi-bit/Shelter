package com.shelter.api.medical; import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
 import java.util.*; public interface VaccinationRepository extends JpaRepository<Vaccination,UUID>{List<Vaccination> findByAnimalIdOrderByAdministeredDateDesc(UUID animalId, Pageable pageable);}