package com.legacyvault.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents a formal, evidence-backed death confirmation claim submitted for a vault owner.
 * Inactivity alone is NEVER proof of death; this workflow requires credible evidence
 * and an authorized reviewer decision before any asset release policies can be evaluated.
 */
@Entity
@Table(name = "death_claims")
public class DeathClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id", nullable = false)
    private TrustedContact contact;

    @Column(name = "claimant_email", nullable = false, length = 255)
    private String claimantEmail;

    @Column(name = "claim_status", columnDefinition = "ENUM('PENDING_OWNER_CHALLENGE', 'PENDING_MANUAL_REVIEW', 'APPROVED_DEATH_VERIFIED', 'REJECTED', 'VETOED_BY_OWNER')", nullable = false)
    private String claimStatus = "PENDING_OWNER_CHALLENGE";

    @Column(name = "evidence_file_name", length = 255)
    private String evidenceFileName;

    @Column(name = "evidence_file_type", length = 100)
    private String evidenceFileType;

    @Column(name = "evidence_file_size")
    private Long evidenceFileSize;

    @Column(name = "evidence_storage_path", length = 500)
    private String evidenceStoragePath;

    @Column(name = "evidence_notes", columnDefinition = "TEXT")
    private String evidenceNotes;

    @Column(name = "challenge_window_expires_at", nullable = false)
    private LocalDateTime challengeWindowExpiresAt;

    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    private User reviewedByUser;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public DeathClaim() {
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

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public TrustedContact getContact() {
        return contact;
    }

    public void setContact(TrustedContact contact) {
        this.contact = contact;
    }

    public String getClaimantEmail() {
        return claimantEmail;
    }

    public void setClaimantEmail(String claimantEmail) {
        this.claimantEmail = claimantEmail;
    }

    public String getClaimStatus() {
        return claimStatus;
    }

    public void setClaimStatus(String claimStatus) {
        this.claimStatus = claimStatus;
    }

    public String getEvidenceFileName() {
        return evidenceFileName;
    }

    public void setEvidenceFileName(String evidenceFileName) {
        this.evidenceFileName = evidenceFileName;
    }

    public String getEvidenceFileType() {
        return evidenceFileType;
    }

    public void setEvidenceFileType(String evidenceFileType) {
        this.evidenceFileType = evidenceFileType;
    }

    public Long getEvidenceFileSize() {
        return evidenceFileSize;
    }

    public void setEvidenceFileSize(Long evidenceFileSize) {
        this.evidenceFileSize = evidenceFileSize;
    }

    public String getEvidenceStoragePath() {
        return evidenceStoragePath;
    }

    public void setEvidenceStoragePath(String evidenceStoragePath) {
        this.evidenceStoragePath = evidenceStoragePath;
    }

    public String getEvidenceNotes() {
        return evidenceNotes;
    }

    public void setEvidenceNotes(String evidenceNotes) {
        this.evidenceNotes = evidenceNotes;
    }

    public LocalDateTime getChallengeWindowExpiresAt() {
        return challengeWindowExpiresAt;
    }

    public void setChallengeWindowExpiresAt(LocalDateTime challengeWindowExpiresAt) {
        this.challengeWindowExpiresAt = challengeWindowExpiresAt;
    }

    public String getReviewNotes() {
        return reviewNotes;
    }

    public void setReviewNotes(String reviewNotes) {
        this.reviewNotes = reviewNotes;
    }

    public User getReviewedByUser() {
        return reviewedByUser;
    }

    public void setReviewedByUser(User reviewedByUser) {
        this.reviewedByUser = reviewedByUser;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
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
        DeathClaim that = (DeathClaim) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
