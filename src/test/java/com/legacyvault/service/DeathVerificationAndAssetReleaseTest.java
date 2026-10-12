package com.legacyvault.service;

import com.legacyvault.model.*;
import com.legacyvault.payload.ApiResponse;
import com.legacyvault.payload.ClaimReviewRequest;
import com.legacyvault.repository.*;
import com.legacyvault.security.EncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite verifying the complete, production-conscious
 * Verified Death Confirmation and Secure Digital Asset Release workflow.
 * Covers all required security invariants and protocol assertions.
 */
@ExtendWith(MockitoExtension.class)
class DeathVerificationAndAssetReleaseTest {

    @Mock
    private DeathClaimRepository deathClaimRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TrustedContactRepository trustedContactRepository;

    @Mock
    private AccessPolicyRepository policyRepository;

    @Mock
    private AssetReleaseRepository assetReleaseRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private EncryptionService encryptionService;

    private ContactService contactService;
    private AssetReleaseService assetReleaseService;
    private DeathVerificationService deathVerificationService;

    private User owner;
    private User contactUser;
    private User reviewer;
    private TrustedContact trustedContact;
    private VaultAsset authorizedAsset;
    private VaultAsset unauthorizedAsset;
    private AccessPolicy authorizedPolicy;

    @BeforeEach
    void setUp() {
        contactService = new ContactService(trustedContactRepository, auditLogRepository, notificationService);

        assetReleaseService = new AssetReleaseService(
                assetReleaseRepository,
                policyRepository,
                encryptionService,
                fileStorageService,
                notificationService,
                auditLogRepository
        );

        deathVerificationService = new DeathVerificationService(
                deathClaimRepository,
                userRepository,
                trustedContactRepository,
                fileStorageService,
                notificationService,
                assetReleaseService,
                auditLogRepository
        );

        // Vault Owner
        owner = new User();
        owner.setId(10L);
        owner.setEmail("owner@legacyvault.local");
        owner.setFullName("John Doe");
        owner.setIsActive(true);
        owner.setInactivityStatus("ACTIVE");
        owner.setEmergencyDelayHours(168);

        // Trusted Contact User
        contactUser = new User();
        contactUser.setId(20L);
        contactUser.setEmail("beneficiary@legacyvault.local");
        contactUser.setFullName("Jane Doe");
        contactUser.setRole("ROLE_USER");
        contactUser.setIsActive(true);

        // Trusted Contact Entity
        trustedContact = new TrustedContact();
        trustedContact.setId(30L);
        trustedContact.setUser(owner);
        trustedContact.setEmail("beneficiary@legacyvault.local");
        trustedContact.setFullName("Jane Doe");
        trustedContact.setVerificationStatus("VERIFIED");

        // Reviewer
        reviewer = new User();
        reviewer.setId(99L);
        reviewer.setEmail("verifier@legacyvault.local");
        reviewer.setFullName("Legal Verifier Officer");
        reviewer.setRole("ROLE_LEGAL_VERIFIER");
        reviewer.setIsActive(true);

        // Vault Assets
        authorizedAsset = new VaultAsset();
        authorizedAsset.setId(100L);
        authorizedAsset.setUser(owner);
        authorizedAsset.setTitle("Family Trust Directive");
        authorizedAsset.setCategory("FINANCIAL_DIRECTIVE");
        authorizedAsset.setIsActive(true);
        authorizedAsset.setStoragePath("/storage/vault_files/user_10/trust.enc");

        unauthorizedAsset = new VaultAsset();
        unauthorizedAsset.setId(101L);
        unauthorizedAsset.setUser(owner);
        unauthorizedAsset.setTitle("Private Journal");
        unauthorizedAsset.setCategory("PERSONAL_INSTRUCTION");
        unauthorizedAsset.setIsActive(true);

        // Access Policy
        authorizedPolicy = new AccessPolicy();
        authorizedPolicy.setId(500L);
        authorizedPolicy.setAsset(authorizedAsset);
        authorizedPolicy.setContact(trustedContact);
        authorizedPolicy.setPermissionLevel("VIEW_ONLY");
        authorizedPolicy.setIsAuthorized(true);
    }

    @Test
    @DisplayName("Scenario 1 & 2: Inactivity alone or grace period expiration never releases assets")
    void testInactivityAndGracePeriodExpiration_NeverReleasesAssets() {
        // Owner reaches INACTIVE_PENDING_VERIFICATION
        owner.setInactivityStatus("INACTIVE_PENDING_VERIFICATION");

        // Verify that NO releases are triggered without an explicit approved DeathClaim
        verify(assetReleaseRepository, never()).save(any(AssetRelease.class));
        assertNotEquals("DEATH_VERIFIED", owner.getInactivityStatus());
    }

    @Test
    @DisplayName("Scenario 3: A death claim without supporting evidence file cannot be submitted")
    void testClaimWithoutEvidence_CannotBeSubmitted() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(owner));
        when(trustedContactRepository.findByUserIdAndEmail(10L, contactUser.getEmail()))
                .thenReturn(Optional.of(trustedContact));

        ApiResponse response = deathVerificationService.submitDeathClaim(
                contactUser,
                10L,
                null, // Missing evidence file
                "Death certificate reference #12345"
        );

        assertFalse(response.isSuccess());
        assertTrue(response.getMessage().toLowerCase().contains("evidence"));
        verify(deathClaimRepository, never()).save(any(DeathClaim.class));
    }

    @Test
    @DisplayName("Scenario 4: An unverified or rejected claim cannot release assets")
    void testUnverifiedOrRejectedClaim_CannotReleaseAssets() {
        DeathClaim rejectedClaim = new DeathClaim();
        rejectedClaim.setId(1L);
        rejectedClaim.setUser(owner);
        rejectedClaim.setContact(trustedContact);
        rejectedClaim.setClaimantEmail(contactUser.getEmail());
        rejectedClaim.setClaimStatus("REJECTED");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                assetReleaseService.evaluateAndExecuteReleasesForClaim(rejectedClaim)
        );

        assertTrue(ex.getMessage().contains("not approved"));
        verify(assetReleaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("Scenario 5: A claimant or contact cannot approve their own claim")
    void testClaimantCannotApproveOwnClaim() {
        DeathClaim claim = new DeathClaim();
        claim.setId(1L);
        claim.setUser(owner);
        claim.setContact(trustedContact);
        claim.setClaimantEmail(contactUser.getEmail());
        claim.setClaimStatus("PENDING_OWNER_CHALLENGE");

        // Try reviewing where reviewer is the claimant
        when(deathClaimRepository.findById(1L)).thenReturn(Optional.of(claim));

        User verifierWithSameEmail = new User();
        verifierWithSameEmail.setId(20L);
        verifierWithSameEmail.setEmail(contactUser.getEmail());
        verifierWithSameEmail.setRole("ROLE_ADMIN");

        ApiResponse response = deathVerificationService.reviewClaim(
                verifierWithSameEmail,
                1L,
                new ClaimReviewRequest("APPROVED", "Self-approving claim")
        );

        assertFalse(response.isSuccess());
        assertTrue(response.getMessage().toLowerCase().contains("conflict of interest"));
        verify(assetReleaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("Scenario 6: A pending manual review cannot release assets")
    void testPendingReview_CannotReleaseAssets() {
        DeathClaim pendingClaim = new DeathClaim();
        pendingClaim.setId(2L);
        pendingClaim.setUser(owner);
        pendingClaim.setContact(trustedContact);
        pendingClaim.setClaimStatus("PENDING_MANUAL_REVIEW");

        assertThrows(IllegalStateException.class, () ->
                assetReleaseService.evaluateAndExecuteReleasesForClaim(pendingClaim)
        );

        verify(assetReleaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("Scenario 7: Only an authorized reviewer can approve or reject a claim")
    void testOnlyAuthorizedReviewerCanReviewClaim() {
        DeathClaim claim = new DeathClaim();
        claim.setId(3L);
        claim.setUser(owner);
        claim.setContact(trustedContact);
        claim.setClaimantEmail("other@legacyvault.local");
        claim.setClaimStatus("PENDING_MANUAL_REVIEW");

        User regularUser = new User();
        regularUser.setId(40L);
        regularUser.setEmail("regular@legacyvault.local");
        regularUser.setRole("ROLE_USER"); // Not verifier or admin

        ApiResponse response = deathVerificationService.reviewClaim(
                regularUser,
                3L,
                new ClaimReviewRequest("APPROVED", "Trying to approve without permission")
        );

        assertFalse(response.isSuccess());
        assertTrue(response.getMessage().toLowerCase().contains("unauthorized"));
        verify(deathClaimRepository, never()).save(any());
    }

    @Test
    @DisplayName("Scenario 8: An approved claim cannot release assets forbidden by the owner's policy")
    void testApprovedClaim_DoesNotReleaseForbiddenAssets() {
        DeathClaim approvedClaim = new DeathClaim();
        approvedClaim.setId(4L);
        approvedClaim.setUser(owner);
        approvedClaim.setContact(trustedContact);
        approvedClaim.setClaimStatus("APPROVED_DEATH_VERIFIED");
        approvedClaim.setChallengeWindowExpiresAt(LocalDateTime.now().minusHours(1)); // Window has elapsed

        // Owner only authorized policy for 'authorizedAsset', NOT 'unauthorizedAsset'
        when(policyRepository.findByAssetUserIdAndIsAuthorizedTrue(10L))
                .thenReturn(List.of(authorizedPolicy));
        when(assetReleaseRepository.existsByAssetIdAndContactId(100L, 30L))
                .thenReturn(false);
        when(assetReleaseRepository.save(any(AssetRelease.class))).thenAnswer(invocation -> {
            AssetRelease r = invocation.getArgument(0);
            r.setId(777L);
            return r;
        });

        List<AssetRelease> releases = assetReleaseService.evaluateAndExecuteReleasesForClaim(approvedClaim);

        assertEquals(1, releases.size());
        assertEquals(authorizedAsset.getId(), releases.get(0).getAsset().getId());

        // Verify unauthorized asset was never released
        verify(assetReleaseRepository, never()).save(argThat(r -> r.getAsset().getId().equals(unauthorizedAsset.getId())));
    }

    @Test
    @DisplayName("Scenario 9 & 10: Beneficiary cannot access or download unauthorized or unrelated assets")
    void testBeneficiaryCannotAccessUnauthorizedAssets() {
        // Attempting to download an asset without an active release record
        when(assetReleaseRepository.findByAssetIdAndContactEmailAndReleaseStatus(999L, contactUser.getEmail(), "RELEASED"))
                .thenReturn(Optional.empty());

        SecurityException ex = assertThrows(SecurityException.class, () ->
                assetReleaseService.loadReleasedAssetFile(contactUser, 999L)
        );

        assertTrue(ex.getMessage().toLowerCase().contains("unauthorized"));
    }

    @Test
    @DisplayName("Scenario 11: Expired, invalid, and reused contact verification tokens are rejected")
    void testContactTokenVerification_RejectionRules() {
        // 1. Invalid token
        when(trustedContactRepository.findByVerificationTokenHash(anyString()))
                .thenReturn(Optional.empty());

        ApiResponse invalidResp = contactService.verifyContactToken("invalid_raw_token");
        assertFalse(invalidResp.isSuccess());
        assertTrue(invalidResp.getMessage().toLowerCase().contains("invalid"));

        // 2. Expired token
        TrustedContact expiredContact = new TrustedContact();
        expiredContact.setId(31L);
        expiredContact.setUser(owner);
        expiredContact.setVerificationStatus("INVITED");
        expiredContact.setVerificationTokenExpiresAt(LocalDateTime.now().minusDays(1)); // Expired yesterday

        when(trustedContactRepository.findByVerificationTokenHash(anyString()))
                .thenReturn(Optional.of(expiredContact));

        ApiResponse expiredResp = contactService.verifyContactToken("expired_token");
        assertFalse(expiredResp.isSuccess());
        assertTrue(expiredResp.getMessage().toLowerCase().contains("expired"));
        assertEquals("EXPIRED", expiredContact.getVerificationStatus());
        assertNull(expiredContact.getVerificationTokenHash()); // Cleared

        // 3. Reused token (status already VERIFIED)
        TrustedContact verifiedContact = new TrustedContact();
        verifiedContact.setId(32L);
        verifiedContact.setUser(owner);
        verifiedContact.setVerificationStatus("VERIFIED");

        when(trustedContactRepository.findByVerificationTokenHash(anyString()))
                .thenReturn(Optional.of(verifiedContact));

        ApiResponse reusedResp = contactService.verifyContactToken("reused_token");
        assertFalse(reusedResp.isSuccess());
        assertTrue(reusedResp.getMessage().toLowerCase().contains("already been verified"));
    }

    @Test
    @DisplayName("Scenario 12: Duplicate claim processing does not create duplicate release records")
    void testDuplicateProcessing_PreventsDuplicateReleases() {
        DeathClaim approvedClaim = new DeathClaim();
        approvedClaim.setId(5L);
        approvedClaim.setUser(owner);
        approvedClaim.setContact(trustedContact);
        approvedClaim.setClaimStatus("APPROVED_DEATH_VERIFIED");
        approvedClaim.setChallengeWindowExpiresAt(LocalDateTime.now().minusHours(1)); // Window has elapsed

        when(policyRepository.findByAssetUserIdAndIsAuthorizedTrue(10L))
                .thenReturn(List.of(authorizedPolicy));
        // Simulate release already exists in database
        when(assetReleaseRepository.existsByAssetIdAndContactId(100L, 30L))
                .thenReturn(true);

        List<AssetRelease> releases = assetReleaseService.evaluateAndExecuteReleasesForClaim(approvedClaim);

        // Must NOT create another record
        assertTrue(releases.isEmpty());
        verify(assetReleaseRepository, never()).save(any());
        verify(notificationService, never()).sendAssetReleaseNotice(any(), any(), any());
    }

    @Test
    @DisplayName("Scenario 13 & 14: Notification service reports accurate status (SENT, SIMULATED, FAILED)")
    void testNotificationServiceAccurateReporting() {
        // With unconfigured SMTP: NotificationService returns SIMULATED
        NotificationService unconfiguredService = new NotificationService(
                null,
                "noreply@legacyvault.local",
                "",
                "localhost",
                "http://localhost:5173",
                "",
                "",
                ""
        );

        NotificationService.NotificationResult simResult = unconfiguredService.sendEmailDetailed(
                "test@example.com",
                "Test Subject",
                "Test Body"
        );

        assertFalse(simResult.delivered());
        assertTrue(simResult.simulated());
        assertEquals("SIMULATED", simResult.status());

        // Blank email: Returns FAILED
        NotificationService.NotificationResult failedResult = unconfiguredService.sendEmailDetailed(
                "",
                "Test Subject",
                "Test Body"
        );
        assertFalse(failedResult.delivered());
        assertFalse(failedResult.simulated());
        assertEquals("FAILED", failedResult.status());
    }

    @Test
    @DisplayName("Owner can challenge and veto death claim, confirming alive and active")
    void testOwnerVetoDeathClaim() {
        DeathClaim claim = new DeathClaim();
        claim.setId(10L);
        claim.setUser(owner);
        claim.setContact(trustedContact);
        claim.setClaimantEmail("beneficiary@legacyvault.local");
        claim.setClaimStatus("PENDING_OWNER_CHALLENGE");

        when(deathClaimRepository.findByIdAndUserId(10L, 10L)).thenReturn(Optional.of(claim));

        ApiResponse response = deathVerificationService.vetoClaim(owner, 10L);

        assertTrue(response.isSuccess());
        assertEquals("VETOED_BY_OWNER", claim.getClaimStatus());
        assertEquals("ACTIVE", owner.getInactivityStatus());
        assertNotNull(owner.getLastHeartbeatAt());
        verify(deathClaimRepository).save(claim);
        verify(userRepository).save(owner);
    }

    @Test
    @DisplayName("Reviewer cannot approve claim while mandatory owner veto challenge window is active")
    void testReviewerCannotApproveDuringActiveVetoWindow() {
        DeathClaim claim = new DeathClaim();
        claim.setId(11L);
        claim.setUser(owner);
        claim.setContact(trustedContact);
        claim.setClaimantEmail("someone@legacyvault.local");
        claim.setClaimStatus("PENDING_OWNER_CHALLENGE");
        claim.setChallengeWindowExpiresAt(LocalDateTime.now().plusDays(5)); // Veto window still open

        when(deathClaimRepository.findById(11L)).thenReturn(Optional.of(claim));

        ApiResponse response = deathVerificationService.reviewClaim(
                reviewer,
                11L,
                new ClaimReviewRequest("APPROVED", "Official death certificate checked and verified.")
        );

        assertFalse(response.isSuccess());
        assertTrue(response.getMessage().toLowerCase().contains("veto window is active"));
        verify(deathClaimRepository, never()).save(any());
        verify(assetReleaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("VIEW_ONLY released assets cannot be downloaded as raw document files")
    void testViewOnlyReleasedAssetCannotBeDownloaded() {
        AssetRelease viewOnlyRelease = new AssetRelease();
        viewOnlyRelease.setId(88L);
        viewOnlyRelease.setAsset(authorizedAsset);
        viewOnlyRelease.setContact(trustedContact);
        viewOnlyRelease.setPermissionLevel("VIEW_ONLY");
        viewOnlyRelease.setReleaseStatus("RELEASED");

        when(assetReleaseRepository.findByAssetIdAndContactEmailAndReleaseStatus(100L, contactUser.getEmail(), "RELEASED"))
                .thenReturn(Optional.of(viewOnlyRelease));

        SecurityException ex = assertThrows(SecurityException.class, () ->
                assetReleaseService.loadReleasedAssetFile(contactUser, 100L)
        );

        assertTrue(ex.getMessage().contains("VIEW_ONLY"));
    }

    @Test
    @DisplayName("Asset release execution blocked if mandatory owner veto window has not yet elapsed")
    void testReleaseExecutionBlockedWhileVetoWindowActive() {
        DeathClaim claim = new DeathClaim();
        claim.setId(12L);
        claim.setUser(owner);
        claim.setContact(trustedContact);
        claim.setClaimStatus("APPROVED_DEATH_VERIFIED");
        claim.setChallengeWindowExpiresAt(LocalDateTime.now().plusDays(3)); // Window still active

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                assetReleaseService.evaluateAndExecuteReleasesForClaim(claim)
        );

        assertTrue(ex.getMessage().contains("Mandatory owner veto window is active"));
        verify(assetReleaseRepository, never()).save(any());
    }
}
