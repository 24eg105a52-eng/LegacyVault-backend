package com.legacyvault.controller;

import com.legacyvault.model.User;
import com.legacyvault.payload.ApiResponse;
import com.legacyvault.payload.VaultAssetRequest;
import com.legacyvault.payload.VaultAssetResponse;
import com.legacyvault.service.VaultService;
import jakarta.validation.Valid;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

/**
 * REST controller managing encrypted digital vault assets.
 * Requires authenticated JWT and enforces user-isolated access controls.
 */
@RestController
@RequestMapping("/api/vault")
public class VaultController {

    private final VaultService vaultService;

    public VaultController(VaultService vaultService) {
        this.vaultService = vaultService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse> getAssets(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, "Authentication required."));
        }
        List<VaultAssetResponse> assets = vaultService.getAssetsForUser(currentUser);
        return ResponseEntity.ok(new ApiResponse(true, "Vault assets retrieved successfully.", assets));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse> getAssetById(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, "Authentication required."));
        }
        Optional<VaultAssetResponse> assetOpt = vaultService.getAssetById(currentUser, id);
        if (assetOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse(false, "Vault asset not found."));
        }
        return ResponseEntity.ok(new ApiResponse(true, "Vault asset retrieved.", assetOpt.get()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse> createAsset(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody VaultAssetRequest request) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, "Authentication required."));
        }
        VaultAssetResponse created = vaultService.createAsset(currentUser, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse(true, "Vault asset secured successfully.", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse> updateAsset(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @Valid @RequestBody VaultAssetRequest request) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, "Authentication required."));
        }
        Optional<VaultAssetResponse> updatedOpt = vaultService.updateAsset(currentUser, id, request);
        if (updatedOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse(false, "Vault asset not found."));
        }
        return ResponseEntity.ok(new ApiResponse(true, "Vault asset updated successfully.", updatedOpt.get()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deleteAsset(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, "Authentication required."));
        }
        boolean deleted = vaultService.deleteAsset(currentUser, id);
        if (!deleted) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse(false, "Vault asset not found."));
        }
        return ResponseEntity.ok(new ApiResponse(true, "Vault asset removed successfully."));
    }

    /**
     * Creates a new vault asset with an attached document file (AES-256-GCM encrypted at rest).
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse> uploadAsset(
            @AuthenticationPrincipal User currentUser,
            @RequestParam("title") String title,
            @RequestParam(value = "category", required = false, defaultValue = "DOCUMENT") String category,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "secretContent", required = false) String secretContent,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, "Authentication required."));
        }
        try {
            VaultAssetRequest request = new VaultAssetRequest(title, category, description, secretContent);
            VaultAssetResponse created = vaultService.createAssetWithFile(currentUser, request, file);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ApiResponse(true, "Document secured with AES-256-GCM encryption.", created));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse(false, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Failed to upload and secure document: " + e.getMessage()));
        }
    }

    /**
     * Updates an existing vault asset with optional document attachment/replacement.
     */
    @PutMapping(value = "/{id}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse> updateAssetWithFile(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @RequestParam("title") String title,
            @RequestParam(value = "category", required = false, defaultValue = "DOCUMENT") String category,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "secretContent", required = false) String secretContent,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, "Authentication required."));
        }
        try {
            VaultAssetRequest request = new VaultAssetRequest(title, category, description, secretContent);
            Optional<VaultAssetResponse> updatedOpt = vaultService.updateAssetWithFile(currentUser, id, request, file);
            if (updatedOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiResponse(false, "Vault asset not found."));
            }
            return ResponseEntity.ok(new ApiResponse(true, "Vault asset updated successfully.", updatedOpt.get()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse(false, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Failed to update asset: " + e.getMessage()));
        }
    }

    /**
     * Decrypts and streams an attached document file to its verified owner.
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<?> downloadAssetFile(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, "Authentication required."));
        }
        try {
            Optional<VaultService.DecryptedFile> fileOpt = vaultService.loadAssetFile(currentUser, id);
            if (fileOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiResponse(false, "No attached document found for this asset."));
            }

            VaultService.DecryptedFile file = fileOpt.get();
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(file.contentType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName() + "\"")
                    .contentLength(file.sizeBytes())
                    .body(new ByteArrayResource(file.content()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse(false, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Failed to download document: " + e.getMessage()));
        }
    }
}
