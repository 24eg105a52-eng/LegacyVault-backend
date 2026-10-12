package com.legacyvault.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents a time-delayed emergency access claim submitted by a trusted contact.
 * Maps to the 'emergency_access_requests' table in MySQL.
 */
@Entity
@Table(name = "emergency_access_requests")
public class EmergencyAccessRequest {

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

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "request_status", columnDefinition = "ENUM('PENDING_OWNER_REVIEW', 'APPROVED_BY_OWNER', 'VETOED_BY_OWNER', 'RELEASED_POST_GRACE_PERIOD', 'REVOKED_EXPIRED')", nullable = false)
    private String requestStatus = "PENDING_OWNER_REVIEW";

    @Column(name = "veto_window_expires_at", nullable = false)
    private LocalDateTime vetoWindowExpiresAt;

    @Column(name = "temporary_token_hash", length = 255)
    private String temporaryTokenHash;

    @Column(name = "access_granted_at")
    private LocalDateTime accessGrantedAt;

    @Column(name = "access_expires_at")
    private LocalDateTime accessExpiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public EmergencyAccessRequest() {
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

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getRequestStatus() {
        return requestStatus;
    }

    public void setRequestStatus(String requestStatus) {
        this.requestStatus = requestStatus;
    }

    public LocalDateTime getVetoWindowExpiresAt() {
        return vetoWindowExpiresAt;
    }

    public void setVetoWindowExpiresAt(LocalDateTime vetoWindowExpiresAt) {
        this.vetoWindowExpiresAt = vetoWindowExpiresAt;
    }

    public String getTemporaryTokenHash() {
        return temporaryTokenHash;
    }

    public void setTemporaryTokenHash(String temporaryTokenHash) {
        this.temporaryTokenHash = temporaryTokenHash;
    }

    public LocalDateTime getAccessGrantedAt() {
        return accessGrantedAt;
    }

    public void setAccessGrantedAt(LocalDateTime accessGrantedAt) {
        this.accessGrantedAt = accessGrantedAt;
    }

    public LocalDateTime getAccessExpiresAt() {
        return accessExpiresAt;
    }

    public void setAccessExpiresAt(LocalDateTime accessExpiresAt) {
        this.accessExpiresAt = accessExpiresAt;
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
        EmergencyAccessRequest that = (EmergencyAccessRequest) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "EmergencyAccessRequest{" +
                "id=" + id +
                ", requestStatus='" + requestStatus + '\'' +
                ", vetoWindowExpiresAt=" + vetoWindowExpiresAt +
                ", accessGrantedAt=" + accessGrantedAt +
                '}';
    }
}
