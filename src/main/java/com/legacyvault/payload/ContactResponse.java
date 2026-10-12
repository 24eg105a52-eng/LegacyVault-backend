package com.legacyvault.payload;

import java.time.LocalDateTime;

/**
 * DTO representing a trusted contact.
 */
public class ContactResponse {

    private Long id;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String relationship;
    private String roleType;
    private String verificationStatus;
    private LocalDateTime createdAt;

    public ContactResponse() {
    }

    public ContactResponse(Long id, String fullName, String email, String phoneNumber,
                           String relationship, String roleType, String verificationStatus,
                           LocalDateTime createdAt) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.relationship = relationship;
        this.roleType = roleType;
        this.verificationStatus = verificationStatus;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getRelationship() {
        return relationship;
    }

    public void setRelationship(String relationship) {
        this.relationship = relationship;
    }

    public String getRoleType() {
        return roleType;
    }

    public void setRoleType(String roleType) {
        this.roleType = roleType;
    }

    public String getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(String verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
