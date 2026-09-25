package com.shelter.api.document;

import com.shelter.api.animal.Animal;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name="medical_documents")
public class MedicalDocument {
    @Id
    @GeneratedValue(strategy=GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="animal_id", nullable=false)
    private Animal animal;

    @Column(name="document_type", nullable=false, length=50)
    private String documentType;
    @Column(nullable=false, length=200)
    private String title;
    @Column(name="file_url", nullable=false, length=1000)
    private String fileUrl;
    @Column(name="document_date")
    private LocalDate documentDate;
    private String notes;
    @Column(name="uploaded_by", length=120)
    private String uploadedBy;
    @Column(name="created_at", nullable=false)
    private OffsetDateTime createdAt;
    @Column(name="storage_key",length=500) private String storageKey;
    @Column(name="original_file_name",length=255) private String originalFileName;
    @Column(name="content_type",length=120) private String contentType;
    @Column(name="file_size") private Long fileSize;

    @PrePersist void prePersist(){ if(createdAt==null) createdAt=OffsetDateTime.now(); }

    public UUID getId(){return id;}
    public Animal getAnimal(){return animal;}
    public void setAnimal(Animal animal){this.animal=animal;}
    public String getDocumentType(){return documentType;}
    public void setDocumentType(String v){documentType=v;}
    public String getTitle(){return title;}
    public void setTitle(String v){title=v;}
    public String getFileUrl(){return fileUrl;}
    public void setFileUrl(String v){fileUrl=v;}
    public LocalDate getDocumentDate(){return documentDate;}
    public void setDocumentDate(LocalDate v){documentDate=v;}
    public String getNotes(){return notes;}
    public void setNotes(String v){notes=v;}
    public String getUploadedBy(){return uploadedBy;}
    public void setUploadedBy(String v){uploadedBy=v;}
    public OffsetDateTime getCreatedAt(){return createdAt;}
    public String getStorageKey(){return storageKey;} public void setStorageKey(String v){storageKey=v;}
    public String getOriginalFileName(){return originalFileName;} public void setOriginalFileName(String v){originalFileName=v;}
    public String getContentType(){return contentType;} public void setContentType(String v){contentType=v;}
    public Long getFileSize(){return fileSize;} public void setFileSize(Long v){fileSize=v;}
}
