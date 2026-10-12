package com.legacyvault.payload;

import jakarta.validation.constraints.NotNull;

public class AccessPolicyRequest {

    @NotNull(message = "Asset ID is required")
    private Long assetId;

    @NotNull(message = "Contact ID is required")
    private Long contactId;

    private String permissionLevel = "VIEW_ONLY";
    private String releaseTrigger = "APPROVED_EMERGENCY_CLAIM";
    private Boolean requiresLegalVerification = false;
    private Boolean isAuthorized = true;
    private String authorizationNotes;

    public AccessPolicyRequest() {
    }

    public AccessPolicyRequest(Long assetId, Long contactId, String permissionLevel, Boolean isAuthorized) {
        this.assetId = assetId;
        this.contactId = contactId;
        this.permissionLevel = permissionLevel;
        this.isAuthorized = isAuthorized;
    }

    public Long getAssetId() {
        return assetId;
    }

    public void setAssetId(Long assetId) {
        this.assetId = assetId;
    }

    public Long getContactId() {
        return contactId;
    }

    public void setContactId(Long contactId) {
        this.contactId = contactId;
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

    public String getAuthorizationNotes() {
        return authorizationNotes;
    }

    public void setAuthorizationNotes(String authorizationNotes) {
        this.authorizationNotes = authorizationNotes;
    }
}
