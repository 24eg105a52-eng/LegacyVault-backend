package com.legacyvault.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Tracks an explicit digital asset release granted to a designated trusted contact.
 * Generated ONLY after an owner's death has been formally verified and approved
 * and the owner's pre-configured AccessPolicy permits access.
 */
@Entity
@Table(
    name = "asset_releases",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_release_asset_contact", columnNames = {"asset_id", "contact_id"})
    }
)
public class AssetRelease {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claim_id", nullable = false)
    private DeathClaim claim;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private VaultAsset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id", nullable = false)
    private TrustedContact contact;

    @Column(name = "permission_level", columnDefinition = "ENUM('VIEW_ONLY', 'FULL_TRANSFER')", nullable = false)
    private String permissionLevel = "VIEW_ONLY";

    @Column(name = "release_status", columnDefinition = "ENUM('RELEASED', 'REVOKED')", nullable = false)
    private String releaseStatus = "RELEASED";

    @Column(name = "access_token_hash", length = 255)
    private String accessTokenHash;

    @Column(name = "access_token_expires_at")
    private LocalDateTime accessTokenExpiresAt;

    @Column(name = "released_at", nullable = false)
    private LocalDateTime releasedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public AssetRelease() {
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (releasedAt == null) {
            releasedAt = LocalDateTime.now();
        }
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public DeathClaim getClaim() {
        return claim;
    }

    public void setClaim(DeathClaim claim) {
        this.claim = claim;
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

    public String getReleaseStatus() {
        return releaseStatus;
    }

    public void setReleaseStatus(String releaseStatus) {
        this.releaseStatus = releaseStatus;
    }

    public String getAccessTokenHash() {
        return accessTokenHash;
    }

    public void setAccessTokenHash(String accessTokenHash) {
        this.accessTokenHash = accessTokenHash;
    }

    public LocalDateTime getAccessTokenExpiresAt() {
        return accessTokenExpiresAt;
    }

    public void setAccessTokenExpiresAt(LocalDateTime accessTokenExpiresAt) {
        this.accessTokenExpiresAt = accessTokenExpiresAt;
    }

    public LocalDateTime getReleasedAt() {
        return releasedAt;
    }

    public void setReleasedAt(LocalDateTime releasedAt) {
        this.releasedAt = releasedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AssetRelease that = (AssetRelease) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
