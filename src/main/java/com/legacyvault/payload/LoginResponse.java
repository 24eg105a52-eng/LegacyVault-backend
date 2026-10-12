package com.legacyvault.payload;

/**
 * Data Transfer Object containing authenticated user profile data.
 * The token field remains null until a production JWT library is integrated.
 */
public class LoginResponse {

    private Long id;
    private String email;
    private String fullName;
    private String role;
    private String inactivityStatus;
    private String token;
    private String tokenType;

    public LoginResponse() {
    }

    public LoginResponse(Long id, String email, String fullName, String role, String inactivityStatus) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.inactivityStatus = inactivityStatus;
        this.token = null;
        this.tokenType = null;
    }

    public LoginResponse(Long id, String email, String fullName, String role, String inactivityStatus, String token, String tokenType) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.inactivityStatus = inactivityStatus;
        this.token = token;
        this.tokenType = tokenType;
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

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }
}
