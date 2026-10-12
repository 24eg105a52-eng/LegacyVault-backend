package com.legacyvault.payload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for creating and updating vault assets.
 */
public class VaultAssetRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String title;

    private String category = "DOCUMENT";

    private String description;

    private String secretContent;

    public VaultAssetRequest() {
    }

    public VaultAssetRequest(String title, String category, String description, String secretContent) {
        this.title = title;
        this.category = category;
        this.description = description;
        this.secretContent = secretContent;
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
}
