package com.shelter.api.audit;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
public class AuditLog {
    @Id
    private UUID id;

    @Column(nullable=false, length=120)
    private String actor;

    @Column(nullable=false, length=80)
    private String action;

    @Column(name="entity_type", nullable=false, length=80)
    private String entityType;

    @Column(name="entity_id")
    private UUID entityId;

    @Column(name="occurred_at", nullable=false)
    private OffsetDateTime occurredAt;

    @Column(columnDefinition="TEXT")
    private String details;

    @PrePersist
    void init() {
        if (id == null) id = UUID.randomUUID();
        if (occurredAt == null) occurredAt = OffsetDateTime.now();
    }

    public UUID getId(){return id;}
    public String getActor(){return actor;} public void setActor(String v){actor=v;}
    public String getAction(){return action;} public void setAction(String v){action=v;}
    public String getEntityType(){return entityType;} public void setEntityType(String v){entityType=v;}
    public UUID getEntityId(){return entityId;} public void setEntityId(UUID v){entityId=v;}
    public OffsetDateTime getOccurredAt(){return occurredAt;}
    public String getDetails(){return details;} public void setDetails(String v){details=v;}
}
