package com.legacyvault.payload;

import java.time.LocalDateTime;

public record AccessPolicyResponse(
        Long id,
        Long assetId,
        String assetTitle,
        String assetCategory,
        Long contactId,
        String contactName,
        String contactEmail,
        String permissionLevel,
        String releaseTrigger,
        Boolean requiresLegalVerification,
        Boolean isAuthorized,
        LocalDateTime authorizedAt,
        String authorizationNotes,
        LocalDateTime createdAt
) {}
