package com.legacyvault.controller;

import com.legacyvault.model.User;
import com.legacyvault.payload.ApiResponse;
import com.legacyvault.payload.BeneficiaryAssetResponse;
import com.legacyvault.service.AssetReleaseService;
import com.legacyvault.service.VaultService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller providing authenticated beneficiaries access to released assets
 * authorized after verified death confirmation.
 */
@RestController
@RequestMapping("/api/beneficiary")
public class BeneficiaryController {

    private final AssetReleaseService assetReleaseService;

    public BeneficiaryController(AssetReleaseService assetReleaseService) {
        this.assetReleaseService = assetReleaseService;
    }

    /**
     * Lists assets explicitly released to the authenticated beneficiary.
     */
    @GetMapping("/assets")
    public ResponseEntity<ApiResponse> getReleasedAssets(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, "Authentication required."));
        }

        List<BeneficiaryAssetResponse> assets = assetReleaseService.getReleasedAssetsForBeneficiary(currentUser);
        return ResponseEntity.ok(new ApiResponse(true, "Released assets retrieved successfully.", assets));
    }

    /**
     * Securely streams and decrypts an authorized document to its verified beneficiary.
     * Enforces server-side authorization check (returns 403 Forbidden if not released to caller).
     */
    @GetMapping("/assets/{assetId}/download")
    public ResponseEntity<?> downloadReleasedAsset(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long assetId) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, "Authentication required."));
        }

        try {
            VaultService.DecryptedFile file = assetReleaseService.loadReleasedAssetFile(currentUser, assetId);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(file.contentType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName() + "\"")
                    .contentLength(file.sizeBytes())
                    .body(new ByteArrayResource(file.content()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse(false, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse(false, "File not found: " + e.getMessage()));
        }
    }
}
