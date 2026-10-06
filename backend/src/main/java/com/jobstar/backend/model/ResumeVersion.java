package com.jobstar.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;
import java.util.List;

@Entity
public class ResumeVersion {

    @Id
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String label;

    @Column(length = 1000)
    private String documentUrl;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String fileName;

    @Column(length = 1000)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String storageKey;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String contentType;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long fileSize;

    @Column(length = 2000)
    private String notes;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "user_id")
    private UserAccount owner;

    @Transient
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private List<ResumeApplicationReference> linkedApplications = List.of();

    public ResumeVersion() {
    }

    public Long getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDocumentUrl() {
        return documentUrl;
    }

    public void setDocumentUrl(String documentUrl) {
        this.documentUrl = documentUrl;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public UserAccount getOwner() {
        return owner;
    }

    public void setOwner(UserAccount owner) {
        this.owner = owner;
    }

    public List<ResumeApplicationReference> getLinkedApplications() {
        return linkedApplications;
    }

    public void setLinkedApplications(List<ResumeApplicationReference> linkedApplications) {
        this.linkedApplications = linkedApplications;
    }
}
