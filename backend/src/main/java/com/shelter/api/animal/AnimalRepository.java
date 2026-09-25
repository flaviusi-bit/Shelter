package com.shelter.api.animal;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AnimalRepository extends JpaRepository<Animal,UUID>{
    List<Animal> findByNameContainingIgnoreCaseOrMicrochipNumberContainingIgnoreCaseOrderByNameAsc(String name,String microchip);
}