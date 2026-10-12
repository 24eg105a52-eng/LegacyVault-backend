package com.legacyvault.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Connects a vault asset with a designated trusted contact under specific release rules.
 * Maps to the 'access_policies' table in MySQL.
 */
@Entity
@Table(
    name = "access_policies",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_policy_asset_contact", columnNames = {"asset_id", "contact_id"})
    }
)
public class AccessPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private VaultAsset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id", nullable = false)
    private TrustedContact contact;

    @Column(name = "permission_level", columnDefinition = "ENUM('VIEW_ONLY', 'FULL_TRANSFER', 'EXECUTOR_DEPUTY')", nullable = false)
    private String permissionLevel = "VIEW_ONLY";

    @Column(name = "release_trigger", columnDefinition = "ENUM('VERIFIED_INACTIVITY_EXPIRY', 'APPROVED_EMERGENCY_CLAIM', 'LEGAL_CONFIRMATION_REQUIRED')", nullable = false)
    private String releaseTrigger = "VERIFIED_INACTIVITY_EXPIRY";

    @Column(name = "requires_legal_verification", nullable = false)
    private Boolean requiresLegalVerification = false;

    @Column(name = "is_authorized", nullable = false)
    private Boolean isAuthorized = false;

    @Column(name = "authorized_at")
    private LocalDateTime authorizedAt;

    @Column(name = "authorization_notes", columnDefinition = "TEXT")
    private String authorizationNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public AccessPolicy() {
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public VaultAsset getAsset() {
        return asset;
    }

    public void setAsset(VaultAsset asset) {
        this.asset = asset;
    }

    public TrustedContact getContact() {
        return contact;
    }

    public void setContact(TrustedContact contact) {
        this.contact = contact;
    }

    public String getPermissionLevel() {
        return permissionLevel;
    }

    public void setPermissionLevel(String permissionLevel) {
        this.permissionLevel = permissionLevel;
    }

    public String getReleaseTrigger() {
        return releaseTrigger;
    }

    public void setReleaseTrigger(String releaseTrigger) {
        this.releaseTrigger = releaseTrigger;
    }

    public Boolean getRequiresLegalVerification() {
        return requiresLegalVerification;
    }

    public void setRequiresLegalVerification(Boolean requiresLegalVerification) {
        this.requiresLegalVerification = requiresLegalVerification;
    }

    public Boolean getIsAuthorized() {
        return isAuthorized;
    }

    public void setIsAuthorized(Boolean isAuthorized) {
        this.isAuthorized = isAuthorized;
    }

    public LocalDateTime getAuthorizedAt() {
        return authorizedAt;
    }

    public void setAuthorizedAt(LocalDateTime authorizedAt) {
        this.authorizedAt = authorizedAt;
    }

    public String getAuthorizationNotes() {
        return authorizationNotes;
    }

    public void setAuthorizationNotes(String authorizationNotes) {
        this.authorizationNotes = authorizationNotes;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AccessPolicy that = (AccessPolicy) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "AccessPolicy{" +
                "id=" + id +
                ", permissionLevel='" + permissionLevel + '\'' +
                ", releaseTrigger='" + releaseTrigger + '\'' +
                ", requiresLegalVerification=" + requiresLegalVerification +
                ", isAuthorized=" + isAuthorized +
                '}';
    }
}
