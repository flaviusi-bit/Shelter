package com.shelter.api.audit;

import org.springframework.stereotype.Service;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AuditLogService {
    private static final int MAX_ACTOR_LENGTH = 120;
    private static final int MAX_ACTION_LENGTH = 80;
    private static final int MAX_ENTITY_TYPE_LENGTH = 80;
    private static final int MAX_DETAILS_LENGTH = 4096;
    private static final int MAX_AUDIT_FIELD_BYTES = 8192;
    private static final Pattern SAFE_ACTOR = Pattern.compile("[\\p{L}\\p{N}._@:+\\- ]+");
    private static final Pattern SAFE_TOKEN = Pattern.compile("[A-Za-z0-9._:-]+");
    private static final int MAX_AUDIT_ID_LENGTH = 64;
    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void record(String actor, String action, String entityType, UUID entityId, String details) {
        String safeActor = actor == null || actor.isBlank() ? "SYSTEM" : actor;
        validateLength("actor", safeActor, MAX_ACTOR_LENGTH);
        validateUtf8Bytes("actor", safeActor);
        if (!SAFE_ACTOR.matcher(safeActor).matches()) throw new IllegalArgumentException("Audit actor is invalid");
        validateLength("action", action, MAX_ACTION_LENGTH);
        validateUtf8Bytes("action", action);
        if (!SAFE_TOKEN.matcher(action).matches()) throw new IllegalArgumentException("Audit action is invalid");
        validateLength("entityType", entityType, MAX_ENTITY_TYPE_LENGTH);
        if (entityId != null && entityId.toString().length() > MAX_AUDIT_ID_LENGTH) {
            throw new IllegalArgumentException("Audit entity id is invalid");
        }
        validateUtf8Bytes("entityType", entityType);
        if (!SAFE_TOKEN.matcher(entityType).matches()) throw new IllegalArgumentException("Audit entity type is invalid");
        validateLength("details", details, MAX_DETAILS_LENGTH);
        validateUtf8Bytes("details", details);
        if (details != null && details.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Audit details are invalid");
        }
        AuditLog entry = new AuditLog();
        entry.setActor(safeActor);
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setDetails(details);
        repository.save(entry);
    }

    private void validateUtf8Bytes(String field, String value) {
        if (value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_AUDIT_FIELD_BYTES) {
            throw new IllegalArgumentException("Audit " + field + " is invalid");
        }
    }

    private void validateLength(String field, String value, int maxLength) {
        if (value == null || value.length() > maxLength) {
            throw new IllegalArgumentException("Audit " + field + " is invalid");
        }
    }
}
