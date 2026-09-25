package com.shelter.api.treatment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface TreatmentAdministrationRepository extends JpaRepository<TreatmentAdministration, UUID> {
    List<TreatmentAdministration> findByTreatmentIdOrderByScheduledAtAsc(UUID treatmentId);
    List<TreatmentAdministration> findByTreatmentAnimalIdAndScheduledAtBetweenOrderByScheduledAtAsc(
        UUID animalId, OffsetDateTime from, OffsetDateTime to);
}
