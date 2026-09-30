package com.shelter.api.treatment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface TreatmentAdministrationRepository extends JpaRepository<TreatmentAdministration, UUID> {
    List<TreatmentAdministration> findByTreatmentIdOrderByScheduledAtAsc(UUID treatmentId, Pageable pageable);
    List<TreatmentAdministration> findByTreatmentIdAndScheduledAtGreaterThanEqualAndScheduledAtLessThanOrderByScheduledAtAsc(UUID treatmentId, OffsetDateTime from, OffsetDateTime to);
    List<TreatmentAdministration> findByScheduledAtGreaterThanEqualAndScheduledAtLessThanOrderByScheduledAtAsc(OffsetDateTime from, OffsetDateTime to, org.springframework.data.domain.Pageable pageable);
    long countByStatusAndScheduledAtGreaterThanEqualAndScheduledAtLessThan(String status, OffsetDateTime from, OffsetDateTime to);
    List<TreatmentAdministration> findByTreatmentAnimalIdAndScheduledAtBetweenOrderByScheduledAtAsc(
        UUID animalId, OffsetDateTime from, OffsetDateTime to);
}
