package com.legacyvault.payload;

import java.time.LocalDateTime;

public record BeneficiaryAssetResponse(
        Long releaseId,
        Long assetId,
        String assetTitle,
        String category,
        String description,
        String secretContent,
        boolean hasAttachment,
        String fileName,
        String fileType,
        Long fileSizeBytes,
        String permissionLevel,
        LocalDateTime releasedAt,
        String ownerName,
        String ownerEmail
) {}
