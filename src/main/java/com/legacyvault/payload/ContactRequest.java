package com.legacyvault.payload;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for creating and editing trusted contacts.
 */
public class ContactRequest {

    @NotBlank(message = "Contact name is required")
    @Size(max = 150, message = "Name must not exceed 150 characters")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Valid email is required")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    @Size(max = 30, message = "Phone must not exceed 30 characters")
    private String phoneNumber;

    @NotBlank(message = "Relationship is required")
    @Size(max = 100, message = "Relationship must not exceed 100 characters")
    private String relationship;

    private String roleType = "BENEFICIARY";

    public ContactRequest() {
    }

    public ContactRequest(String fullName, String email, String phoneNumber, String relationship, String roleType) {
        this.fullName = fullName;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.relationship = relationship;
        this.roleType = roleType;
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
}
