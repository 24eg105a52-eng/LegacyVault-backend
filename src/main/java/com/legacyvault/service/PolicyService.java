package com.legacyvault.service;

import com.legacyvault.model.AccessPolicy;
import com.legacyvault.model.AuditLog;
import com.legacyvault.model.TrustedContact;
import com.legacyvault.model.User;
import com.legacyvault.model.VaultAsset;
import com.legacyvault.payload.AccessPolicyRequest;
import com.legacyvault.payload.AccessPolicyResponse;
import com.legacyvault.payload.ApiResponse;
import com.legacyvault.repository.AccessPolicyRepository;
import com.legacyvault.repository.AuditLogRepository;
import com.legacyvault.repository.TrustedContactRepository;
import com.legacyvault.repository.VaultAssetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service managing asset-specific release policies with strict server-side owner validation.
 * Owners configure which trusted contacts can access specific assets upon verified death confirmation.
 */
@Service
public class PolicyService {

    private static final Set<String> ALLOWED_PERMISSIONS = Set.of(
            "VIEW_ONLY",
            "FULL_TRANSFER",
            "EXECUTOR_DEPUTY"
    );

    private static final Set<String> ALLOWED_TRIGGERS = Set.of(
            "VERIFIED_INACTIVITY_EXPIRY",
            "APPROVED_EMERGENCY_CLAIM",
            "LEGAL_CONFIRMATION_REQUIRED"
    );

    private final AccessPolicyRepository policyRepository;
    private final VaultAssetRepository assetRepository;
    private final TrustedContactRepository contactRepository;
    private final AuditLogRepository auditLogRepository;

    public PolicyService(AccessPolicyRepository policyRepository,
                         VaultAssetRepository assetRepository,
                         TrustedContactRepository contactRepository,
                         AuditLogRepository auditLogRepository) {
        this.policyRepository = policyRepository;
        this.assetRepository = assetRepository;
        this.contactRepository = contactRepository;
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Lists all access policies configured by the authenticated owner.
     */
    @Transactional(readOnly = true)
    public List<AccessPolicyResponse> getPoliciesForOwner(User owner) {
        return policyRepository.findByAssetUserId(owner.getId())
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Creates or updates an asset release policy with strict server-side ownership checks.
     */
    @Transactional
    public ApiResponse savePolicy(User owner, AccessPolicyRequest request) {
        // Validate asset ownership
        Optional<VaultAsset> assetOpt = assetRepository.findByIdAndUserId(request.getAssetId(), owner.getId());
        if (assetOpt.isEmpty() || !Boolean.TRUE.equals(assetOpt.get().getIsActive())) {
            return new ApiResponse(false, "Asset not found or does not belong to you.");
        }
        VaultAsset asset = assetOpt.get();

        // Validate contact ownership
        Optional<TrustedContact> contactOpt = contactRepository.findByIdAndUserId(request.getContactId(), owner.getId());
        if (contactOpt.isEmpty()) {
            return new ApiResponse(false, "Trusted contact not found or does not belong to your vault.");
        }
        TrustedContact contact = contactOpt.get();

        String permission = request.getPermissionLevel() != null && ALLOWED_PERMISSIONS.contains(request.getPermissionLevel())
                ? request.getPermissionLevel()
                : "VIEW_ONLY";

        String trigger = request.getReleaseTrigger() != null && ALLOWED_TRIGGERS.contains(request.getReleaseTrigger())
                ? request.getReleaseTrigger()
                : "APPROVED_EMERGENCY_CLAIM";

        boolean isAuthorized = request.getIsAuthorized() == null || request.getIsAuthorized();

        // Look up existing policy for (asset, contact)
        Optional<AccessPolicy> existingOpt = policyRepository.findByAssetIdAndContactId(asset.getId(), contact.getId());
        AccessPolicy policy = existingOpt.orElseGet(AccessPolicy::new);

        policy.setAsset(asset);
        policy.setContact(contact);
        policy.setPermissionLevel(permission);
        policy.setReleaseTrigger(trigger);
        policy.setRequiresLegalVerification(Boolean.TRUE.equals(request.getRequiresLegalVerification()));
        policy.setIsAuthorized(isAuthorized);
        policy.setAuthorizationNotes(request.getAuthorizationNotes());

        if (isAuthorized) {
            policy.setAuthorizedAt(LocalDateTime.now());
        }

        AccessPolicy saved = policyRepository.save(policy);

        recordAuditLog(owner, "POLICY_CONFIGURED", String.valueOf(saved.getId()),
                "Configured release policy: Contact " + contact.getFullName() +
                        " for Asset '" + asset.getTitle() + "' [Permission: " + permission + ", Authorized: " + isAuthorized + "]");

        return new ApiResponse(true, "Release policy saved successfully.", mapToResponse(saved));
    }

    /**
     * Deletes a release policy ensuring owner isolation.
     */
    @Transactional
    public boolean deletePolicy(User owner, Long policyId) {
        Optional<AccessPolicy> policyOpt = policyRepository.findByIdAndAssetUserId(policyId, owner.getId());
        if (policyOpt.isEmpty()) {
            return false;
        }

        AccessPolicy policy = policyOpt.get();
        policyRepository.delete(policy);

        recordAuditLog(owner, "POLICY_REMOVED", String.valueOf(policyId),
                "Removed release policy ID " + policyId + " for asset '" + policy.getAsset().getTitle() + "'");

        return true;
    }

    private AccessPolicyResponse mapToResponse(AccessPolicy policy) {
        return new AccessPolicyResponse(
                policy.getId(),
                policy.getAsset().getId(),
                policy.getAsset().getTitle(),
                policy.getAsset().getCategory(),
                policy.getContact().getId(),
                policy.getContact().getFullName(),
                policy.getContact().getEmail(),
                policy.getPermissionLevel(),
                policy.getReleaseTrigger(),
                policy.getRequiresLegalVerification(),
                policy.getIsAuthorized(),
                policy.getAuthorizedAt(),
                policy.getAuthorizationNotes(),
                policy.getCreatedAt()
        );
    }

    private void recordAuditLog(User user, String action, String resourceId, String details) {
        AuditLog audit = new AuditLog();
        audit.setUser(user);
        audit.setActorType("VAULT_OWNER");
        audit.setActorIdentifier(user.getEmail());
        audit.setAction(action);
        audit.setResourceType("ACCESS_POLICY");
        audit.setResourceId(resourceId);
        audit.setDetails(details);
        auditLogRepository.save(audit);
    }
}
