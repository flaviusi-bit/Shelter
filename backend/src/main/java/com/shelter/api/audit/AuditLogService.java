package com.shelter.api.audit;

import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class AuditLogService {
    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void record(String actor, String action, String entityType, UUID entityId, String details) {
        AuditLog entry = new AuditLog();
        entry.setActor(actor == null || actor.isBlank() ? "SYSTEM" : actor);
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setDetails(details);
        repository.save(entry);
    }
}
