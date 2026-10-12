package com.legacyvault.service;

import com.legacyvault.model.*;
import com.legacyvault.payload.ApiResponse;
import com.legacyvault.payload.ClaimReviewRequest;
import com.legacyvault.payload.DeathClaimResponse;
import com.legacyvault.repository.AuditLogRepository;
import com.legacyvault.repository.DeathClaimRepository;
import com.legacyvault.repository.TrustedContactRepository;
import com.legacyvault.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service managing formal, evidence-backed death confirmation workflows.
 *
 * CRITICAL ESTATE SECURITY RULE:
 * Stopping check-ins or inactivity is NEVER proof of death.
 * Only verified death claims approved with official evidence by an authorized reviewer
 * can transition an account to DEATH_VERIFIED and authorize asset release policies.
 */
@Service
public class DeathVerificationService {

    private static final Logger logger = LoggerFactory.getLogger(DeathVerificationService.class);

    private static final Set<String> AUTHORIZED_REVIEWER_ROLES = Set.of(
            "ROLE_ADMIN",
            "ROLE_LEGAL_VERIFIER",
            "ADMIN",
            "LEGAL_VERIFIER"
    );

    private final DeathClaimRepository deathClaimRepository;
    private final UserRepository userRepository;
    private final TrustedContactRepository trustedContactRepository;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;
    private final AssetReleaseService assetReleaseService;
    private final AuditLogRepository auditLogRepository;

    public DeathVerificationService(DeathClaimRepository deathClaimRepository,
                                    UserRepository userRepository,
                                    TrustedContactRepository trustedContactRepository,
                                    FileStorageService fileStorageService,
                                    NotificationService notificationService,
                                    AssetReleaseService assetReleaseService,
                                    AuditLogRepository auditLogRepository) {
        this.deathClaimRepository = deathClaimRepository;
        this.userRepository = userRepository;
        this.trustedContactRepository = trustedContactRepository;
        this.fileStorageService = fileStorageService;
        this.notificationService = notificationService;
        this.assetReleaseService = assetReleaseService;
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Submits a formal, evidence-backed death confirmation claim.
     */
    @Transactional
    public ApiResponse submitDeathClaim(User claimantUser, Long ownerId, MultipartFile evidenceFile, String evidenceNotes) {
        if (claimantUser == null) {
            return new ApiResponse(false, "Authentication required to submit a claim.");
        }

        Optional<User> ownerOpt = userRepository.findById(ownerId);
        if (ownerOpt.isEmpty()) {
            return new ApiResponse(false, "Vault owner not found.");
        }
        User owner = ownerOpt.get();

        // Rule 1: Owner cannot file a death claim against themselves
        if (owner.getId().equals(claimantUser.getId()) || owner.getEmail().equalsIgnoreCase(claimantUser.getEmail())) {
            return new ApiResponse(false, "You cannot submit a death confirmation claim against your own account.");
        }

        // Rule 2: Owner cannot already be settled
        if ("SETTLED".equalsIgnoreCase(owner.getInactivityStatus())) {
            return new ApiResponse(false, "Estate for this vault owner has already been settled.");
        }

        // Rule 3: Claimant must be a designated contact of the owner
        String normalizedClaimantEmail = claimantUser.getEmail().trim().toLowerCase();
        Optional<TrustedContact> contactOpt = trustedContactRepository.findByUserIdAndEmail(owner.getId(), normalizedClaimantEmail);
        if (contactOpt.isEmpty()) {
            return new ApiResponse(false, "You are not designated as a trusted contact for this vault owner.");
        }
        TrustedContact contact = contactOpt.get();

        // Rule 4: Contact must be explicitly verified
        if (!"VERIFIED".equalsIgnoreCase(contact.getVerificationStatus())) {
            return new ApiResponse(false, "Your trusted contact email must be verified before submitting a formal death claim.");
        }

        // Rule 5: Credible supporting evidence file is required
        if (evidenceFile == null || evidenceFile.isEmpty()) {
            return new ApiResponse(false, "Supporting official evidence (e.g. death certificate) is required.");
        }

        // Rule 6: Mandatory evidence notes / document citation
        if (!StringUtils.hasText(evidenceNotes)) {
            return new ApiResponse(false, "Official evidence notes or certificate registration number are mandatory.");
        }

        // Rule 7: Prevent duplicate pending claims
        boolean claimAlreadyPending = deathClaimRepository.existsByUserIdAndClaimStatusIn(
                owner.getId(),
                List.of("PENDING_OWNER_CHALLENGE", "PENDING_MANUAL_REVIEW")
        );
        if (claimAlreadyPending) {
            return new ApiResponse(false, "A death confirmation claim is already pending review for this vault owner.");
        }

        int challengeHours = owner.getEmergencyDelayHours() != null ? owner.getEmergencyDelayHours() : 168; // 7 days
        LocalDateTime challengeExpiresAt = LocalDateTime.now().plusHours(challengeHours);

        DeathClaim claim = new DeathClaim();
        claim.setUser(owner);
        claim.setContact(contact);
        claim.setClaimantEmail(normalizedClaimantEmail);
        claim.setClaimStatus("PENDING_OWNER_CHALLENGE");
        claim.setChallengeWindowExpiresAt(challengeExpiresAt);
        claim.setEvidenceNotes(evidenceNotes.trim());

        // Save initial claim to obtain ID for file storage isolation
        DeathClaim saved = deathClaimRepository.save(claim);

        try {
            FileStorageService.StoredFileInfo fileInfo = fileStorageService.storeEvidenceFile(saved.getId(), evidenceFile);
            saved.setEvidenceFileName(fileInfo.originalFilename());
            saved.setEvidenceFileType(fileInfo.contentType());
            saved.setEvidenceFileSize(fileInfo.sizeBytes());
            saved.setEvidenceStoragePath(fileInfo.storagePath());
            saved = deathClaimRepository.save(saved);
        } catch (IOException e) {
            logger.error("[DeathVerification] Failed to encrypt evidence file: {}", e.getMessage());
            throw new RuntimeException("Failed to securely store evidence document: " + e.getMessage(), e);
        }

        // Dispatch notifications
        notificationService.sendDeathClaimAlertToOwner(owner, saved, challengeHours / 24);
        notificationService.sendClaimSubmissionReceipt(normalizedClaimantEmail, owner, saved);

        recordAuditLog(owner, "CLAIMANT", normalizedClaimantEmail, "DEATH_CLAIM_SUBMITTED",
                String.valueOf(saved.getId()),
                "Submitted death claim #" + saved.getId() + " with evidence file: " + saved.getEvidenceFileName());

        return new ApiResponse(true, "Death confirmation claim submitted successfully. Owner challenge window is active.", mapToResponse(saved));
    }

    /**
     * Allows the vault owner to veto a false or fraudulent claim, proving they are alive.
     */
    @Transactional
    public ApiResponse vetoClaim(User owner, Long claimId) {
        Optional<DeathClaim> claimOpt = deathClaimRepository.findByIdAndUserId(claimId, owner.getId());
        if (claimOpt.isEmpty()) {
            return new ApiResponse(false, "Claim not found.");
        }

        DeathClaim claim = claimOpt.get();
        if ("APPROVED_DEATH_VERIFIED".equalsIgnoreCase(claim.getClaimStatus()) ||
                "VETOED_BY_OWNER".equalsIgnoreCase(claim.getClaimStatus())) {
            return new ApiResponse(false, "Claim cannot be vetoed in its current status (" + claim.getClaimStatus() + ").");
        }

        claim.setClaimStatus("VETOED_BY_OWNER");
        deathClaimRepository.save(claim);

        // Reset owner account to ACTIVE and update heartbeat
        owner.setInactivityStatus("ACTIVE");
        owner.setLastHeartbeatAt(LocalDateTime.now());
        userRepository.save(owner);

        notificationService.sendClaimReviewDecision(claim, "VETOED_BY_OWNER",
                "The vault owner has actively challenged and vetoed this claim, confirming they are alive.");

        recordAuditLog(owner, "VAULT_OWNER", owner.getEmail(), "DEATH_CLAIM_VETOED",
                String.valueOf(claim.getId()),
                "Owner successfully challenged and vetoed Claim #" + claim.getId() + ". Confirmed alive.");

        return new ApiResponse(true, "Claim successfully vetoed. Your account has been verified alive and restored to ACTIVE.", mapToResponse(claim));
    }

    /**
     * Authorized human reviewer workflow to evaluate claims, inspect evidence, and approve or reject.
     */
    @Transactional
    public ApiResponse reviewClaim(User reviewer, Long claimId, ClaimReviewRequest request) {
        if (reviewer == null) {
            return new ApiResponse(false, "Authentication required.");
        }

        // Strict authorization: Reviewer must have legal verifier or admin role
        String role = reviewer.getRole() != null ? reviewer.getRole() : "ROLE_USER";
        if (!AUTHORIZED_REVIEWER_ROLES.contains(role)) {
            return new ApiResponse(false, "Unauthorized: Only administrators or authorized legal verifiers can review claims.");
        }

        Optional<DeathClaim> claimOpt = deathClaimRepository.findById(claimId);
        if (claimOpt.isEmpty()) {
            return new ApiResponse(false, "Claim not found.");
        }
        DeathClaim claim = claimOpt.get();

        // Conflict-of-interest defense 1: Claimant cannot review their own claim
        if (claim.getClaimantEmail().equalsIgnoreCase(reviewer.getEmail())) {
            return new ApiResponse(false, "Conflict of interest: You cannot review or approve a claim you submitted.");
        }

        // Conflict-of-interest defense 2: Reviewer cannot be the target owner
        if (claim.getUser().getId().equals(reviewer.getId())) {
            return new ApiResponse(false, "Conflict of interest: You cannot review a claim concerning your own estate.");
        }

        if (!StringUtils.hasText(request.getReviewNotes())) {
            return new ApiResponse(false, "Written review notes documenting verification justification are mandatory.");
        }

        String decision = request.getDecision() != null ? request.getDecision().trim().toUpperCase() : "";
        if (!decision.equals("APPROVED") && !decision.equals("REJECTED")) {
            return new ApiResponse(false, "Invalid decision. Must be APPROVED or REJECTED.");
        }

        if (decision.equals("APPROVED")) {
            // Rule: Enforce mandatory owner challenge / veto window before final approval and release
            if (claim.getChallengeWindowExpiresAt() != null && LocalDateTime.now().isBefore(claim.getChallengeWindowExpiresAt())) {
                return new ApiResponse(false, "Cannot approve claim: Mandatory owner veto window is active until "
                        + claim.getChallengeWindowExpiresAt() + ". Vault owner has the right to challenge/veto.");
            }

            claim.setClaimStatus("APPROVED_DEATH_VERIFIED");
            claim.setReviewNotes(request.getReviewNotes().trim());
            claim.setReviewedByUser(reviewer);
            claim.setReviewedAt(LocalDateTime.now());
            DeathClaim updated = deathClaimRepository.save(claim);

            // Transition user inactivity_status to DEATH_VERIFIED
            User owner = claim.getUser();
            owner.setInactivityStatus("DEATH_VERIFIED");
            userRepository.save(owner);

            // Trigger asset release policy evaluation
            assetReleaseService.evaluateAndExecuteReleasesForClaim(updated);

            notificationService.sendClaimReviewDecision(updated, "APPROVED_DEATH_VERIFIED", request.getReviewNotes().trim());

            recordAuditLog(owner, "ADMIN", reviewer.getEmail(), "DEATH_CLAIM_APPROVED",
                    String.valueOf(updated.getId()),
                    "Approved death confirmation claim #" + updated.getId() + ". Justification: " + request.getReviewNotes());

            return new ApiResponse(true, "Death claim approved. Digital asset release policies evaluated.", mapToResponse(updated));
        } else {
            claim.setClaimStatus("REJECTED");
            claim.setReviewNotes(request.getReviewNotes().trim());
            claim.setReviewedByUser(reviewer);
            claim.setReviewedAt(LocalDateTime.now());
            DeathClaim updated = deathClaimRepository.save(claim);

            notificationService.sendClaimReviewDecision(updated, "REJECTED", request.getReviewNotes().trim());

            recordAuditLog(claim.getUser(), "ADMIN", reviewer.getEmail(), "DEATH_CLAIM_REJECTED",
                    String.valueOf(updated.getId()),
                    "Rejected death claim #" + updated.getId() + ". Reason: " + request.getReviewNotes());

            return new ApiResponse(true, "Death claim rejected. Assets remain locked.", mapToResponse(updated));
        }
    }

    /**
     * Loads supporting evidence document for authorized reviewers, claimants, or target owners.
     */
    @Transactional(readOnly = true)
    public VaultService.DecryptedFile loadEvidenceFile(User requester, Long claimId) throws IOException {
        Optional<DeathClaim> claimOpt = deathClaimRepository.findById(claimId);
        if (claimOpt.isEmpty()) {
            throw new FileStorageService.FileNotFoundException("Claim not found.");
        }
        DeathClaim claim = claimOpt.get();

        String role = requester.getRole() != null ? requester.getRole() : "ROLE_USER";
        boolean isAuthorizedReviewer = AUTHORIZED_REVIEWER_ROLES.contains(role);
        boolean isClaimant = claim.getClaimantEmail().equalsIgnoreCase(requester.getEmail());
        boolean isOwner = claim.getUser().getId().equals(requester.getId());

        if (!isAuthorizedReviewer && !isClaimant && !isOwner) {
            throw new SecurityException("Unauthorized access attempt to claim evidence document.");
        }

        if (claim.getEvidenceStoragePath() == null) {
            throw new FileStorageService.FileNotFoundException("No evidence document attached to this claim.");
        }

        byte[] decrypted = fileStorageService.loadAndDecryptDirectly(claim.getEvidenceStoragePath());

        return new VaultService.DecryptedFile(
                claim.getEvidenceFileName(),
                claim.getEvidenceFileType() != null ? claim.getEvidenceFileType() : "application/pdf",
                claim.getEvidenceFileSize() != null ? claim.getEvidenceFileSize() : decrypted.length,
                decrypted
        );
    }

    @Transactional(readOnly = true)
    public List<DeathClaimResponse> getClaimsForOwner(User owner) {
        return deathClaimRepository.findByUserIdOrderByCreatedAtDesc(owner.getId())
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DeathClaimResponse> getClaimsByClaimant(User claimant) {
        return deathClaimRepository.findByClaimantEmailOrderByCreatedAtDesc(claimant.getEmail().trim().toLowerCase())
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DeathClaimResponse> getAllClaimsForReviewer(User reviewer) {
        String role = reviewer.getRole() != null ? reviewer.getRole() : "ROLE_USER";
        if (!AUTHORIZED_REVIEWER_ROLES.contains(role)) {
            return List.of();
        }
        return deathClaimRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    private DeathClaimResponse mapToResponse(DeathClaim claim) {
        return new DeathClaimResponse(
                claim.getId(),
                claim.getUser().getId(),
                claim.getUser().getFullName(),
                claim.getUser().getEmail(),
                claim.getContact().getId(),
                claim.getContact().getFullName(),
                claim.getClaimantEmail(),
                claim.getClaimStatus(),
                claim.getEvidenceFileName(),
                claim.getEvidenceFileType(),
                claim.getEvidenceFileSize(),
                claim.getEvidenceNotes(),
                claim.getChallengeWindowExpiresAt(),
                claim.getReviewNotes(),
                claim.getReviewedByUser() != null ? claim.getReviewedByUser().getEmail() : null,
                claim.getReviewedAt(),
                claim.getCreatedAt()
        );
    }

    private void recordAuditLog(User user, String actorType, String actorIdentifier, String action, String resourceId, String details) {
        AuditLog audit = new AuditLog();
        audit.setUser(user);
        audit.setActorType(actorType);
        audit.setActorIdentifier(actorIdentifier);
        audit.setAction(action);
        audit.setResourceType("DEATH_CLAIM");
        audit.setResourceId(resourceId);
        audit.setDetails(details);
        auditLogRepository.save(audit);
    }
}
