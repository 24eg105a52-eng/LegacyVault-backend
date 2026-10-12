package com.legacyvault.payload;

import java.time.LocalDateTime;

public record DeathClaimResponse(
        Long id,
        Long ownerId,
        String ownerName,
        String ownerEmail,
        Long contactId,
        String contactName,
        String claimantEmail,
        String claimStatus,
        String evidenceFileName,
        String evidenceFileType,
        Long evidenceFileSize,
        String evidenceNotes,
        LocalDateTime challengeWindowExpiresAt,
        String reviewNotes,
        String reviewedByEmail,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt
) {}
