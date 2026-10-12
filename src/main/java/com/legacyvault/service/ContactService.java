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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Service managing trusted contacts and designated inheritance beneficiaries.
 */
@Service
public class ContactService {

    private static final Set<String> ALLOWED_ROLES = Set.of(
            "BENEFICIARY",
            "EMERGENCY_DEPUTY",
            "LEGAL_VERIFIER"
    );

    private final TrustedContactRepository trustedContactRepository;
    private final AuditLogRepository auditLogRepository;

    public ContactService(TrustedContactRepository trustedContactRepository,
                          AuditLogRepository auditLogRepository) {
        this.trustedContactRepository = trustedContactRepository;
        this.auditLogRepository = auditLogRepository;
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
     * Creates a new trusted contact for the authenticated user.
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

        TrustedContact contact = new TrustedContact();
        contact.setUser(user);
        contact.setFullName(request.getFullName().trim());
        contact.setEmail(normalizedEmail);
        contact.setPhoneNumber(request.getPhoneNumber() != null ? request.getPhoneNumber().trim() : null);
        contact.setRelationship(request.getRelationship().trim());
        contact.setRoleType(role);
        contact.setVerificationStatus("INVITED");
        contact.setVerificationTokenHash(UUID.randomUUID().toString());

        TrustedContact saved = trustedContactRepository.save(contact);

        recordAuditLog(user, "CONTACT_ADDED", String.valueOf(saved.getId()),
                "Added trusted contact: " + saved.getFullName() + " (" + saved.getEmail() + ")");

        return new ApiResponse(true, "Trusted contact added successfully.", mapToResponse(saved));
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

    private ContactResponse mapToResponse(TrustedContact contact) {
        return new ContactResponse(
                contact.getId(),
                contact.getFullName(),
                contact.getEmail(),
                contact.getPhoneNumber(),
                contact.getRelationship(),
                contact.getRoleType(),
                contact.getVerificationStatus(),
                contact.getCreatedAt()
        );
    }

    private void recordAuditLog(User user, String action, String resourceId, String details) {
        AuditLog auditLog = new AuditLog();
        auditLog.setUser(user);
        auditLog.setActorType("VAULT_OWNER");
        auditLog.setActorIdentifier(user.getEmail());
        auditLog.setAction(action);
        auditLog.setResourceType("TRUSTED_CONTACT");
        auditLog.setResourceId(resourceId);
        auditLog.setDetails(details);
        auditLogRepository.save(auditLog);
    }
}
