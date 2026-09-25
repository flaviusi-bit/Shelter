package com.shelter.api.task;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {
    List<Task> findByStatusOrderByDueAtAsc(String status);
    List<Task> findByStatusAndDueAtBeforeOrderByDueAtAsc(String status, OffsetDateTime dueAt);
    java.util.Optional<Task> findBySourceKey(String sourceKey);
}
