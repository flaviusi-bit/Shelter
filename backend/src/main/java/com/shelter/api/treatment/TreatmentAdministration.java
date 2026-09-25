package com.shelter.api.treatment;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "treatment_administrations")
public class TreatmentAdministration {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "treatment_id")
    private Treatment treatment;

    @Column(name = "scheduled_at", nullable = false)
    private OffsetDateTime scheduledAt;

    @Column(name = "administered_at")
    private OffsetDateTime administeredAt;

    @Column(name = "administered_by", length = 120)
    private String administeredBy;

    @Column(nullable = false, length = 30)
    private String status = "SCHEDULED";

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void init() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public Treatment getTreatment() { return treatment; }
    public void setTreatment(Treatment treatment) { this.treatment = treatment; }
    public OffsetDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(OffsetDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
    public OffsetDateTime getAdministeredAt() { return administeredAt; }
    public void setAdministeredAt(OffsetDateTime administeredAt) { this.administeredAt = administeredAt; }
    public String getAdministeredBy() { return administeredBy; }
    public void setAdministeredBy(String administeredBy) { this.administeredBy = administeredBy; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
