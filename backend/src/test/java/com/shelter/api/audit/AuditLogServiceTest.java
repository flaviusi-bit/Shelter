package com.shelter.api.audit;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuditLogServiceTest {

    @Test
    void recordsAuditEntryWithAllFields() {
        AuditLogRepository repository = mock(AuditLogRepository.class);
        AuditLogService service = new AuditLogService(repository);
        UUID entityId = UUID.randomUUID();

        service.record("alice", "UPDATE_ANIMAL", "ANIMAL", entityId, "Bella");

        var captor = org.mockito.ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        AuditLog entry = captor.getValue();

        assertEquals("alice", entry.getActor());
        assertEquals("UPDATE_ANIMAL", entry.getAction());
        assertEquals("ANIMAL", entry.getEntityType());
        assertEquals(entityId, entry.getEntityId());
        assertEquals("Bella", entry.getDetails());
    }

    @Test
    void defaultsBlankActorToSystem() {
        AuditLogRepository repository = mock(AuditLogRepository.class);
        AuditLogService service = new AuditLogService(repository);

        service.record(" ", "AUTO_SYNC_MEDICAL_REMINDERS", "TASK", null, "created=2");

        var captor = org.mockito.ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        AuditLog entry = captor.getValue();

        assertEquals("SYSTEM", entry.getActor());
        assertNull(entry.getEntityId());
        assertEquals("AUTO_SYNC_MEDICAL_REMINDERS", entry.getAction());
    }
}
