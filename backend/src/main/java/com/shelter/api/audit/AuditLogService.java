package com.shelter.api.audit;

import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class AuditLogService {
    private static final int MAX_ACTOR_LENGTH = 120;
    private static final int MAX_ACTION_LENGTH = 80;
    private static final int MAX_ENTITY_TYPE_LENGTH = 80;
    private static final int MAX_DETAILS_LENGTH = 4096;
    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void record(String actor, String action, String entityType, UUID entityId, String details) {
        String safeActor = actor == null || actor.isBlank() ? "SYSTEM" : actor;
        validateLength("actor", safeActor, MAX_ACTOR_LENGTH);
        validateLength("action", action, MAX_ACTION_LENGTH);
        validateLength("entityType", entityType, MAX_ENTITY_TYPE_LENGTH);
        validateLength("details", details, MAX_DETAILS_LENGTH);
        AuditLog entry = new AuditLog();
        entry.setActor(safeActor);
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setDetails(details);
        repository.save(entry);
    }

    private void validateLength(String field, String value, int maxLength) {
        if (value == null || value.length() > maxLength) {
            throw new IllegalArgumentException("Audit " + field + " is invalid");
        }
    }
}
