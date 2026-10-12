package com.legacyvault.controller;

import com.legacyvault.model.User;
import com.legacyvault.payload.ApiResponse;
import com.legacyvault.payload.ClaimReviewRequest;
import com.legacyvault.payload.DeathClaimResponse;
import com.legacyvault.service.DeathVerificationService;
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

/**
 * REST controller managing formal death confirmation claims,
 * owner veto challenges, reviewer decisions, and evidence inspections.
 */
@RestController
@RequestMapping("/api/claims")
public class DeathClaimController {

    private final DeathVerificationService deathVerificationService;

    public DeathClaimController(DeathVerificationService deathVerificationService) {
        this.deathVerificationService = deathVerificationService;
    }

    /**
     * Submits a formal death confirmation claim with official supporting evidence.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse> submitClaim(
            @AuthenticationPrincipal User currentUser,
            @RequestParam("ownerId") Long ownerId,
            @RequestParam("evidenceFile") MultipartFile evidenceFile,
            @RequestParam("evidenceNotes") String evidenceNotes) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, "Authentication required."));
        }

        ApiResponse response = deathVerificationService.submitDeathClaim(currentUser, ownerId, evidenceFile, evidenceNotes);
        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lists claims filed by the currently authenticated user.
     */
    @GetMapping("/my-claims")
    public ResponseEntity<ApiResponse> getMyClaims(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, "Authentication required."));
        }
        List<DeathClaimResponse> claims = deathVerificationService.getClaimsByClaimant(currentUser);
        return ResponseEntity.ok(new ApiResponse(true, "Claims retrieved successfully.", claims));
    }

    /**
     * Lists claims filed against the authenticated user (for owner challenge / veto).
     */
    @GetMapping("/owner-claims")
    public ResponseEntity<ApiResponse> getOwnerClaims(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, "Authentication required."));
        }
        List<DeathClaimResponse> claims = deathVerificationService.getClaimsForOwner(currentUser);
        return ResponseEntity.ok(new ApiResponse(true, "Owner claims retrieved successfully.", claims));
    }

    /**
     * Owner challenges and vetoes a pending death claim, proving active survival.
     */
    @PostMapping("/{id}/veto")
    public ResponseEntity<ApiResponse> vetoClaim(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, "Authentication required."));
        }
        ApiResponse response = deathVerificationService.vetoClaim(currentUser, id);
        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Review queue for authorized legal verifiers / administrators.
     */
    @GetMapping("/review-queue")
    public ResponseEntity<ApiResponse> getReviewQueue(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, "Authentication required."));
        }
        List<DeathClaimResponse> claims = deathVerificationService.getAllClaimsForReviewer(currentUser);
        return ResponseEntity.ok(new ApiResponse(true, "Review queue retrieved successfully.", claims));
    }

    /**
     * Records an authorized human reviewer decision (APPROVED or REJECTED) with mandatory justification notes.
     */
    @PostMapping("/{id}/review")
    public ResponseEntity<ApiResponse> reviewClaim(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @Valid @RequestBody ClaimReviewRequest request) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, "Authentication required."));
        }
        ApiResponse response = deathVerificationService.reviewClaim(currentUser, id, request);
        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Downloads supporting evidence file for authorized reviewers, claimants, or target owners.
     */
    @GetMapping("/{id}/evidence")
    public ResponseEntity<?> downloadEvidence(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, "Authentication required."));
        }

        try {
            VaultService.DecryptedFile file = deathVerificationService.loadEvidenceFile(currentUser, id);
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
                    .body(new ApiResponse(false, "Evidence file not found: " + e.getMessage()));
        }
    }
}
