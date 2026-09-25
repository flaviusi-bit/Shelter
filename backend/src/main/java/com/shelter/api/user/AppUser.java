package com.shelter.api.user;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
public class AppUser {
    @Id private UUID id;
    @Column(nullable=false,length=80,unique=true) private String username;
    @Column(name="display_name",nullable=false,length=120) private String displayName;
    @Column(name="password_hash",nullable=false,length=255) private String passwordHash;
    @Column(nullable=false,length=30) private String role;
    @Column(nullable=false) private boolean active = true;
    @Column(name="created_at",nullable=false) private OffsetDateTime createdAt;

    @PrePersist void init(){ if(id==null)id=UUID.randomUUID(); if(createdAt==null)createdAt=OffsetDateTime.now(); }
    public UUID getId(){return id;}
    public String getUsername(){return username;} public void setUsername(String v){username=v;}
    public String getDisplayName(){return displayName;} public void setDisplayName(String v){displayName=v;}
    public String getPasswordHash(){return passwordHash;} public void setPasswordHash(String v){passwordHash=v;}
    public String getRole(){return role;} public void setRole(String v){role=v;}
    public boolean isActive(){return active;} public void setActive(boolean v){active=v;}
    public OffsetDateTime getCreatedAt(){return createdAt;}
}
