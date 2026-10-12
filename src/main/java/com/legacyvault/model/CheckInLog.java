package com.legacyvault.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Records heartbeat and check-in events from a vault owner.
 * Maps to the 'check_in_logs' table in MySQL.
 */
@Entity
@Table(name = "check_in_logs")
public class CheckInLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "check_in_type", columnDefinition = "ENUM('MANUAL_PORTAL_PING', 'SESSION_LOGIN', 'EMAIL_CONFIRMATION_LINK')", nullable = false)
    private String checkInType = "MANUAL_PORTAL_PING";

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private LocalDateTime recordedAt;

    public CheckInLog() {
    }

    @PrePersist
    protected void onCreate() {
        if (recordedAt == null) {
            recordedAt = LocalDateTime.now();
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

    public String getCheckInType() {
        return checkInType;
    }

    public void setCheckInType(String checkInType) {
        this.checkInType = checkInType;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(LocalDateTime recordedAt) {
        this.recordedAt = recordedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CheckInLog that = (CheckInLog) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "CheckInLog{" +
                "id=" + id +
                ", checkInType='" + checkInType + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                ", recordedAt=" + recordedAt +
                '}';
    }
}
