package com.shelter.api.task;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {
    List<Task> findByStatusOrderByDueAtAscIdAsc(String status);
    List<Task> findByStatusOrderByDueAtAscIdAsc(String status, Pageable pageable);
    List<Task> findByStatusAndDueAtBeforeOrderByDueAtAsc(String status, OffsetDateTime dueAt);
    long countByStatusAndDueAtBefore(String status, OffsetDateTime dueAt);
    long countByStatusAndDueAtGreaterThanEqualAndDueAtBefore(String status, OffsetDateTime from, OffsetDateTime to);
    java.util.Optional<Task> findBySourceKey(String sourceKey);
    List<Task> findBySourceKeyIn(java.util.Collection<String> sourceKeys);

    @Modifying
    @Query("update Task t set t.status = :newStatus, t.completedAt = :completedAt, t.completedBy = :completedBy where t.id = :id and t.status = 'OPEN'")
    int transitionOpenTask(@Param("id") UUID id, @Param("newStatus") String newStatus,
                           @Param("completedAt") OffsetDateTime completedAt, @Param("completedBy") String completedBy);
}
