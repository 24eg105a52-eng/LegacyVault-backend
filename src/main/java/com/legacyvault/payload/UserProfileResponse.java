package com.legacyvault.payload;

import java.time.LocalDateTime;

/**
 * Data Transfer Object representing the authenticated user's profile.
 * Excludes password hash and sensitive cryptographic master secrets.
 */
public class UserProfileResponse {

    private Long id;
    private String email;
    private String fullName;
    private String phoneNumber;
    private String role;
    private String inactivityStatus;
    private Integer inactivityThresholdDays;
    private Integer gracePeriodDays;
    private Integer emergencyDelayHours;
    private LocalDateTime lastHeartbeatAt;
    private LocalDateTime createdAt;

    public UserProfileResponse() {
    }

    public UserProfileResponse(Long id, String email, String fullName, String phoneNumber,
                               String role, String inactivityStatus, Integer inactivityThresholdDays,
                               Integer gracePeriodDays, Integer emergencyDelayHours,
                               LocalDateTime lastHeartbeatAt, LocalDateTime createdAt) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.phoneNumber = phoneNumber;
        this.role = role;
        this.inactivityStatus = inactivityStatus;
        this.inactivityThresholdDays = inactivityThresholdDays;
        this.gracePeriodDays = gracePeriodDays;
        this.emergencyDelayHours = emergencyDelayHours;
        this.lastHeartbeatAt = lastHeartbeatAt;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getInactivityStatus() {
        return inactivityStatus;
    }

    public void setInactivityStatus(String inactivityStatus) {
        this.inactivityStatus = inactivityStatus;
    }

    public Integer getInactivityThresholdDays() {
        return inactivityThresholdDays;
    }

    public void setInactivityThresholdDays(Integer inactivityThresholdDays) {
        this.inactivityThresholdDays = inactivityThresholdDays;
    }

    public Integer getGracePeriodDays() {
        return gracePeriodDays;
    }

    public void setGracePeriodDays(Integer gracePeriodDays) {
        this.gracePeriodDays = gracePeriodDays;
    }

    public Integer getEmergencyDelayHours() {
        return emergencyDelayHours;
    }

    public void setEmergencyDelayHours(Integer emergencyDelayHours) {
        this.emergencyDelayHours = emergencyDelayHours;
    }

    public LocalDateTime getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public void setLastHeartbeatAt(LocalDateTime lastHeartbeatAt) {
        this.lastHeartbeatAt = lastHeartbeatAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
