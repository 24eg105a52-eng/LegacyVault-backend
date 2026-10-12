package com.legacyvault.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Tracks multi-stage warning notices sent to a vault owner during inactivity.
 * Maps to the 'grace_period_reminders' table in MySQL.
 */
@Entity
@Table(name = "grace_period_reminders")
public class GracePeriodReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "reminder_number", nullable = false)
    private Integer reminderNumber;

    @Column(name = "sent_to_email", nullable = false, length = 255)
    private String sentToEmail;

    @Column(name = "grace_period_expires_at", nullable = false)
    private LocalDateTime gracePeriodExpiresAt;

    @Column(name = "status", columnDefinition = "ENUM('SENT', 'SIMULATED', 'FAILED', 'ACKNOWLEDGED_BY_USER', 'EXPIRED_WITHOUT_RESPONSE')", nullable = false)
    private String status = "SENT";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public GracePeriodReminder() {
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
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

    public Integer getReminderNumber() {
        return reminderNumber;
    }

    public void setReminderNumber(Integer reminderNumber) {
        this.reminderNumber = reminderNumber;
    }

    public String getSentToEmail() {
        return sentToEmail;
    }

    public void setSentToEmail(String sentToEmail) {
        this.sentToEmail = sentToEmail;
    }

    public LocalDateTime getGracePeriodExpiresAt() {
        return gracePeriodExpiresAt;
    }

    public void setGracePeriodExpiresAt(LocalDateTime gracePeriodExpiresAt) {
        this.gracePeriodExpiresAt = gracePeriodExpiresAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
        GracePeriodReminder that = (GracePeriodReminder) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "GracePeriodReminder{" +
                "id=" + id +
                ", reminderNumber=" + reminderNumber +
                ", sentToEmail='" + sentToEmail + '\'' +
                ", status='" + status + '\'' +
                ", gracePeriodExpiresAt=" + gracePeriodExpiresAt +
                '}';
    }
}
