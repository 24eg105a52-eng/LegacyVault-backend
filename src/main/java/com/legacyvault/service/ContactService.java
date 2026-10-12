package com.legacyvault.service;

import com.legacyvault.model.AuditLog;
import com.legacyvault.model.TrustedContact;
import com.legacyvault.model.User;
import com.legacyvault.payload.ApiResponse;
import com.legacyvault.payload.ContactRequest;
import com.legacyvault.payload.ContactResponse;
import com.legacyvault.repository.AuditLogRepository;
import com.legacyvault.repository.TrustedContactRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service managing trusted contacts, cryptographically secure invitation tokens,
 * single-use token verification, replay defense, and resend operations.
 */
@Service
public class ContactService {

    private static final Set<String> ALLOWED_ROLES = Set.of(
            "BENEFICIARY",
            "EMERGENCY_DEPUTY",
            "LEGAL_VERIFIER"
    );

    private static final int INVITATION_EXPIRY_DAYS = 7;

    private final TrustedContactRepository trustedContactRepository;
    private final AuditLogRepository auditLogRepository;
    private final NotificationService notificationService;
    private final SecureRandom secureRandom;

    public ContactService(TrustedContactRepository trustedContactRepository,
                          AuditLogRepository auditLogRepository,
                          NotificationService notificationService) {
        this.trustedContactRepository = trustedContactRepository;
        this.auditLogRepository = auditLogRepository;
        this.notificationService = notificationService;
        this.secureRandom = new SecureRandom();
    }

    /**
     * Lists contacts belonging exclusively to the authenticated user.
     */
    @Transactional(readOnly = true)
    public List<ContactResponse> getContactsForUser(User user) {
        return trustedContactRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Creates a new trusted contact with a single-use verification token and dispatches an invitation email.
     */
    @Transactional
    public ApiResponse addContact(User user, ContactRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (trustedContactRepository.existsByUserIdAndEmail(user.getId(), normalizedEmail)) {
            return new ApiResponse(false, "A contact with email '" + normalizedEmail + "' already exists in your vault.");
        }

        String role = request.getRoleType() != null && ALLOWED_ROLES.contains(request.getRoleType())
                ? request.getRoleType()
                : "BENEFICIARY";

        String rawToken = generateRawToken();
        String tokenHash = hashToken(rawToken);
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(INVITATION_EXPIRY_DAYS);

        TrustedContact contact = new TrustedContact();
        contact.setUser(user);
        contact.setFullName(request.getFullName().trim());
        contact.setEmail(normalizedEmail);
        contact.setPhoneNumber(request.getPhoneNumber() != null ? request.getPhoneNumber().trim() : null);
        contact.setRelationship(request.getRelationship().trim());
        contact.setRoleType(role);
        contact.setVerificationStatus("INVITED");
        contact.setVerificationTokenHash(tokenHash);
        contact.setVerificationTokenExpiresAt(expiresAt);

        TrustedContact saved = trustedContactRepository.save(contact);

        // Send invitation notification
        notificationService.sendContactInvitation(saved, rawToken, user.getFullName());

        recordAuditLog(user, "CONTACT_ADDED", String.valueOf(saved.getId()),
                "Added trusted contact: " + saved.getFullName() + " (" + saved.getEmail() + ") - dispatched invitation token.");

        return new ApiResponse(true, "Trusted contact added and invitation email sent.", mapToResponse(saved));
    }

    /**
     * Resends an invitation with a fresh single-use cryptographic token.
     */
    @Transactional
    public ApiResponse resendInvitation(User user, Long contactId) {
        Optional<TrustedContact> contactOpt = trustedContactRepository.findByIdAndUserId(contactId, user.getId());
        if (contactOpt.isEmpty()) {
            return new ApiResponse(false, "Contact not found.");
        }

        TrustedContact contact = contactOpt.get();
        if ("VERIFIED".equalsIgnoreCase(contact.getVerificationStatus())) {
            return new ApiResponse(false, "Contact '" + contact.getFullName() + "' has already been verified.");
        }

        String rawToken = generateRawToken();
        String tokenHash = hashToken(rawToken);
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(INVITATION_EXPIRY_DAYS);

        contact.setVerificationStatus("INVITED");
        contact.setVerificationTokenHash(tokenHash);
        contact.setVerificationTokenExpiresAt(expiresAt);

        TrustedContact updated = trustedContactRepository.save(contact);

        notificationService.sendContactInvitation(updated, rawToken, user.getFullName());

        recordAuditLog(user, "CONTACT_INVITATION_RESENT", String.valueOf(updated.getId()),
                "Resent invitation to trusted contact: " + updated.getEmail());

        return new ApiResponse(true, "Invitation resent successfully.", mapToResponse(updated));
    }

    /**
     * Public verification endpoint method that validates a single-use token, expires it,
     * and transitions contact to VERIFIED while strictly preventing token reuse / replay attacks.
     */
    @Transactional
    public ApiResponse verifyContactToken(String rawToken) {
        if (!StringUtils.hasText(rawToken)) {
            return new ApiResponse(false, "Verification token cannot be blank.");
        }

        String tokenHash = hashToken(rawToken.trim());
        Optional<TrustedContact> contactOpt = trustedContactRepository.findByVerificationTokenHash(tokenHash);

        if (contactOpt.isEmpty()) {
            return new ApiResponse(false, "Invalid, unrecognized, or already used verification token.");
        }

        TrustedContact contact = contactOpt.get();

        // Check if token has expired
        if (contact.getVerificationTokenExpiresAt() != null && LocalDateTime.now().isAfter(contact.getVerificationTokenExpiresAt())) {
            contact.setVerificationStatus("EXPIRED");
            contact.setVerificationTokenHash(null);
            contact.setVerificationTokenExpiresAt(null);
            trustedContactRepository.save(contact);

            return new ApiResponse(false, "This verification token has expired. Please ask the vault owner to resend the invitation.");
        }

        // Check current status
        if ("VERIFIED".equalsIgnoreCase(contact.getVerificationStatus())) {
            return new ApiResponse(false, "This trusted contact invitation has already been verified.");
        }

        if ("REVOKED".equalsIgnoreCase(contact.getVerificationStatus())) {
            return new ApiResponse(false, "This contact invitation has been revoked by the vault owner.");
        }

        // Successfully verify and invalidate single-use token
        contact.setVerificationStatus("VERIFIED");
        contact.setVerifiedAt(LocalDateTime.now());
        contact.setVerificationTokenHash(null); // Cleared to enforce single-use / prevent replay
        contact.setVerificationTokenExpiresAt(null);

        TrustedContact verified = trustedContactRepository.save(contact);

        recordAuditLog(verified.getUser(), "CONTACT_VERIFIED", String.valueOf(verified.getId()),
                "Trusted contact " + verified.getFullName() + " (" + verified.getEmail() + ") successfully verified identity via single-use token.");

        return new ApiResponse(true, "Your trusted contact email has been verified successfully. Note: Vault assets remain strictly locked until the owner's death is formally confirmed and verified.", mapToResponse(verified));
    }

    /**
     * Removes a contact with strict owner isolation.
     */
    @Transactional
    public boolean deleteContact(User user, Long contactId) {
        Optional<TrustedContact> contactOpt = trustedContactRepository.findByIdAndUserId(contactId, user.getId());
        if (contactOpt.isEmpty()) {
            return false;
        }

        TrustedContact contact = contactOpt.get();
        trustedContactRepository.delete(contact);

        recordAuditLog(user, "CONTACT_REMOVED", String.valueOf(contactId),
                "Removed trusted contact: " + contact.getFullName());

        return true;
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private ContactResponse mapToResponse(TrustedContact contact) {
        return new ContactResponse(
                contact.getId(),
                contact.getFullName(),
                contact.getEmail(),
                contact.getPhoneNumber(),
                contact.getRelationship(),
                contact.getRoleType(),
                contact.getVerificationStatus(),
                contact.getVerifiedAt(),
                contact.getVerificationTokenExpiresAt(),
                contact.getCreatedAt()
        );
    }

    private void recordAuditLog(User user, String action, String resourceId, String details) {
        AuditLog auditLog = new AuditLog();
        auditLog.setUser(user);
        auditLog.setActorType("VAULT_OWNER");
        auditLog.setActorIdentifier(user != null ? user.getEmail() : "SYSTEM");
        auditLog.setAction(action);
        auditLog.setResourceType("TRUSTED_CONTACT");
        auditLog.setResourceId(resourceId);
        auditLog.setDetails(details);
        auditLogRepository.save(auditLog);
    }
}
