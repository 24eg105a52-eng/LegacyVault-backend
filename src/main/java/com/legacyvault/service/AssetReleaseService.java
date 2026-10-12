package com.legacyvault.service;

import com.legacyvault.model.*;
import com.legacyvault.payload.BeneficiaryAssetResponse;
import com.legacyvault.repository.AccessPolicyRepository;
import com.legacyvault.repository.AssetReleaseRepository;
import com.legacyvault.repository.AuditLogRepository;
import com.legacyvault.security.EncryptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service managing authorized digital asset releases to verified beneficiaries.
 * Assets are released ONLY after formal death confirmation is approved
 * and the owner's pre-configured AccessPolicy explicitly authorizes access.
 */
@Service
public class AssetReleaseService {

    private static final Logger logger = LoggerFactory.getLogger(AssetReleaseService.class);

    private final AssetReleaseRepository assetReleaseRepository;
    private final AccessPolicyRepository policyRepository;
    private final EncryptionService encryptionService;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;
    private final AuditLogRepository auditLogRepository;

    public AssetReleaseService(AssetReleaseRepository assetReleaseRepository,
                               AccessPolicyRepository policyRepository,
                               EncryptionService encryptionService,
                               FileStorageService fileStorageService,
                               NotificationService notificationService,
                               AuditLogRepository auditLogRepository) {
        this.assetReleaseRepository = assetReleaseRepository;
        this.policyRepository = policyRepository;
        this.encryptionService = encryptionService;
        this.fileStorageService = fileStorageService;
        this.notificationService = notificationService;
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Evaluates owner policies and creates release records for verified contacts
     * after an authorized death confirmation claim is approved.
     * Prevents duplicate releases and redundant notification emails.
     */
    @Transactional
    public List<AssetRelease> evaluateAndExecuteReleasesForClaim(DeathClaim claim) {
        if (!"APPROVED_DEATH_VERIFIED".equalsIgnoreCase(claim.getClaimStatus())) {
            throw new IllegalStateException("Cannot release assets: Death claim #" + claim.getId() + " is not approved.");
        }

        // Strict security rule: Mandatory owner veto window must have fully elapsed before ANY release execution
        if (claim.getChallengeWindowExpiresAt() != null && LocalDateTime.now().isBefore(claim.getChallengeWindowExpiresAt())) {
            throw new IllegalStateException("Cannot execute asset releases: Mandatory owner veto window is active until "
                    + claim.getChallengeWindowExpiresAt() + ". Release execution blocked.");
        }

        User owner = claim.getUser();
        List<AccessPolicy> authorizedPolicies = policyRepository.findByAssetUserIdAndIsAuthorizedTrue(owner.getId());
        List<AssetRelease> generatedReleases = new ArrayList<>();

        for (AccessPolicy policy : authorizedPolicies) {
            TrustedContact contact = policy.getContact();
            VaultAsset asset = policy.getAsset();

            // Strict condition 1: Contact must be explicitly VERIFIED
            if (!"VERIFIED".equalsIgnoreCase(contact.getVerificationStatus())) {
                logger.warn("[AssetReleaseService] Skipping release of asset '{}' to contact '{}': contact is not VERIFIED (status: {})",
                        asset.getTitle(), contact.getEmail(), contact.getVerificationStatus());
                continue;
            }

            // Strict condition 2: Asset must be active
            if (!Boolean.TRUE.equals(asset.getIsActive())) {
                continue;
            }

            // Strict condition 3: Prevent duplicate release records
            if (assetReleaseRepository.existsByAssetIdAndContactId(asset.getId(), contact.getId())) {
                logger.debug("[AssetReleaseService] Release already exists for asset {} and contact {}", asset.getId(), contact.getId());
                continue;
            }

            String permission = policy.getPermissionLevel() != null ? policy.getPermissionLevel() : "VIEW_ONLY";

            AssetRelease release = new AssetRelease();
            release.setClaim(claim);
            release.setAsset(asset);
            release.setContact(contact);
            release.setPermissionLevel(permission);
            release.setReleaseStatus("RELEASED");
            release.setReleasedAt(LocalDateTime.now());

            AssetRelease saved = assetReleaseRepository.save(release);
            if (saved != null) {
                generatedReleases.add(saved);
                // Dispatch notification email to contact
                notificationService.sendAssetReleaseNotice(contact, asset, permission);

                recordAuditLog(owner, "ASSET_RELEASED", String.valueOf(saved.getId() != null ? saved.getId() : 0L),
                        "Released asset '" + asset.getTitle() + "' to verified beneficiary " + contact.getFullName() +
                                " (" + contact.getEmail() + ") under Claim #" + claim.getId());
            }
        }

        return generatedReleases;
    }

    /**
     * Lists assets released to the authenticated beneficiary, with decrypted secret content.
     */
    @Transactional(readOnly = true)
    public List<BeneficiaryAssetResponse> getReleasedAssetsForBeneficiary(User beneficiary) {
        String normalizedEmail = beneficiary.getEmail().trim().toLowerCase();
        List<AssetRelease> releases = assetReleaseRepository.findByContactEmailAndReleaseStatus(normalizedEmail, "RELEASED");

        return releases.stream()
                .filter(r -> Boolean.TRUE.equals(r.getAsset().getIsActive()))
                .map(release -> {
                    VaultAsset asset = release.getAsset();
                    String decryptedSecret = null;

                    if (asset.getEncryptedContent() != null && asset.getEncryptionIv() != null) {
                        try {
                            decryptedSecret = encryptionService.decrypt(asset.getEncryptedContent(), asset.getEncryptionIv());
                        } catch (Exception e) {
                            decryptedSecret = "[Decryption unavailable]";
                        }
                    }

                    boolean hasFile = asset.getStoragePath() != null;
                    return new BeneficiaryAssetResponse(
                            release.getId(),
                            asset.getId(),
                            asset.getTitle(),
                            asset.getCategory(),
                            asset.getDescription(),
                            decryptedSecret,
                            hasFile,
                            asset.getFileName(),
                            asset.getFileType(),
                            asset.getFileSizeBytes(),
                            release.getPermissionLevel(),
                            release.getReleasedAt(),
                            asset.getUser().getFullName(),
                            asset.getUser().getEmail()
                    );
                })
                .toList();
    }

    /**
     * Loads and decrypts an authorized document file for a verified beneficiary.
     * Enforces strict server-side authorization check: returns 403 if unreleased.
     */
    @Transactional(readOnly = true)
    public VaultService.DecryptedFile loadReleasedAssetFile(User beneficiary, Long assetId) throws IOException {
        String normalizedEmail = beneficiary.getEmail().trim().toLowerCase();

        Optional<AssetRelease> releaseOpt = assetReleaseRepository
                .findByAssetIdAndContactEmailAndReleaseStatus(assetId, normalizedEmail, "RELEASED");

        if (releaseOpt.isEmpty()) {
            throw new SecurityException("Unauthorized: You do not have an active released grant for this asset.");
        }

        AssetRelease release = releaseOpt.get();
        if ("VIEW_ONLY".equalsIgnoreCase(release.getPermissionLevel())) {
            throw new SecurityException("Forbidden: Your grant is VIEW_ONLY and does not permit downloading original document files.");
        }

        VaultAsset asset = release.getAsset();

        if (asset.getStoragePath() == null) {
            throw new FileStorageService.FileNotFoundException("No attached file is associated with this asset.");
        }

        byte[] decryptedBytes = fileStorageService.loadAndDecryptDirectly(asset.getStoragePath());

        recordAuditLog(beneficiary, "BENEFICIARY_DOCUMENT_DOWNLOADED", String.valueOf(asset.getId()),
                "Beneficiary " + beneficiary.getEmail() + " downloaded released document: " + asset.getFileName());

        return new VaultService.DecryptedFile(
                asset.getFileName(),
                asset.getFileType() != null ? asset.getFileType() : "application/octet-stream",
                asset.getFileSizeBytes() != null ? asset.getFileSizeBytes() : decryptedBytes.length,
                decryptedBytes
        );
    }

    private void recordAuditLog(User user, String action, String resourceId, String details) {
        AuditLog audit = new AuditLog();
        audit.setUser(user);
        audit.setActorType("BENEFICIARY");
        audit.setActorIdentifier(user != null ? user.getEmail() : "SYSTEM");
        audit.setAction(action);
        audit.setResourceType("ASSET_RELEASE");
        audit.setResourceId(resourceId);
        audit.setDetails(details);
        auditLogRepository.save(audit);
    }
}
