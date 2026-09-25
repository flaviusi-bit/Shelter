package com.shelter.api.task;

import com.shelter.api.animal.Animal;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name="tasks")
public class Task {
    @Id
    @GeneratedValue(strategy=GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="animal_id")
    private Animal animal;
    @Column(name="task_type",nullable=false,length=40) private String taskType;
    @Column(nullable=false,length=200) private String title;
    @Column(name="due_at",nullable=false) private OffsetDateTime dueAt;
    @Column(nullable=false,length=20) private String status="OPEN";
    @Column(nullable=false,length=20) private String priority="NORMAL";
    @Column(name="assigned_to",length=120) private String assignedTo;
    private String notes;
    @Column(name="created_by",length=120) private String createdBy;
    @Column(name="source_key",length=180,unique=true) private String sourceKey;
    @Column(name="completed_at") private OffsetDateTime completedAt;
    @Column(name="completed_by",length=120) private String completedBy;
    @Column(name="created_at",nullable=false) private OffsetDateTime createdAt;

    @PrePersist void prePersist(){if(createdAt==null)createdAt=OffsetDateTime.now();}

    public UUID getId(){return id;}
    public Animal getAnimal(){return animal;} public void setAnimal(Animal v){animal=v;}
    public String getTaskType(){return taskType;} public void setTaskType(String v){taskType=v;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public OffsetDateTime getDueAt(){return dueAt;} public void setDueAt(OffsetDateTime v){dueAt=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getPriority(){return priority;} public void setPriority(String v){priority=v;}
    public String getAssignedTo(){return assignedTo;} public void setAssignedTo(String v){assignedTo=v;}
    public String getNotes(){return notes;} public void setNotes(String v){notes=v;}
    public String getCreatedBy(){return createdBy;} public void setCreatedBy(String v){createdBy=v;}
    public String getSourceKey(){return sourceKey;} public void setSourceKey(String v){sourceKey=v;}
    public OffsetDateTime getCompletedAt(){return completedAt;} public void setCompletedAt(OffsetDateTime v){completedAt=v;}
    public String getCompletedBy(){return completedBy;} public void setCompletedBy(String v){completedBy=v;}
    public OffsetDateTime getCreatedAt(){return createdAt;}
}
