package com.legacyvault.payload;

import java.time.LocalDateTime;

/**
 * DTO returned to authorized users representing a vault asset and optional attached encrypted document.
 */
public class VaultAssetResponse {

    private Long id;
    private String title;
    private String category;
    private String description;
    private String secretContent;
    private boolean encrypted;
    private String fileName;
    private String fileType;
    private Long fileSizeBytes;
    private boolean hasFile;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public VaultAssetResponse() {
    }

    public VaultAssetResponse(Long id, String title, String category, String description,
                              String secretContent, boolean encrypted,
                              LocalDateTime createdAt, LocalDateTime updatedAt) {
        this(id, title, category, description, secretContent, encrypted, null, null, null, createdAt, updatedAt);
    }

    public VaultAssetResponse(Long id, String title, String category, String description,
                              String secretContent, boolean encrypted,
                              String fileName, String fileType, Long fileSizeBytes,
                              LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.title = title;
        this.category = category;
        this.description = description;
        this.secretContent = secretContent;
        this.encrypted = encrypted;
        this.fileName = fileName;
        this.fileType = fileType;
        this.fileSizeBytes = fileSizeBytes;
        this.hasFile = fileName != null && !fileName.isEmpty();
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSecretContent() {
        return secretContent;
    }

    public void setSecretContent(String secretContent) {
        this.secretContent = secretContent;
    }

    public boolean isEncrypted() {
        return encrypted;
    }

    public void setEncrypted(boolean encrypted) {
        this.encrypted = encrypted;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public boolean isHasFile() {
        return hasFile;
    }

    public void setHasFile(boolean hasFile) {
        this.hasFile = hasFile;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
