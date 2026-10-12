package com.legacyvault.service;

import com.legacyvault.model.DeathClaim;
import com.legacyvault.model.TrustedContact;
import com.legacyvault.model.User;
import com.legacyvault.model.VaultAsset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Service managing real SMTP email and Twilio SMS notification dispatch,
 * with accurate status reporting (SENT, SIMULATED, FAILED) and safe fallbacks.
 */
@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    public record NotificationResult(boolean delivered, boolean simulated, String status, String message) {
        public static NotificationResult sent(String msg) {
            return new NotificationResult(true, false, "SENT", msg);
        }
        public static NotificationResult simulated(String msg) {
            return new NotificationResult(false, true, "SIMULATED", msg);
        }
        public static NotificationResult failed(String msg) {
            return new NotificationResult(false, false, "FAILED", msg);
        }
    }

    private final JavaMailSender mailSender;
    private final String mailFrom;
    private final String mailUsername;
    private final String mailHost;
    private final String appBaseUrl;

    private final String twilioAccountSid;
    private final String twilioAuthToken;
    private final String twilioFromPhone;

    private final HttpClient httpClient;

    public NotificationService(
            @Autowired(required = false) JavaMailSender mailSender,
            @Value("${vault.mail.from:noreply@legacyvault.local}") String mailFrom,
            @Value("${spring.mail.username:}") String mailUsername,
            @Value("${spring.mail.host:localhost}") String mailHost,
            @Value("${vault.app.url:http://localhost:5173}") String appBaseUrl,
            @Value("${vault.sms.account-sid:}") String twilioAccountSid,
            @Value("${vault.sms.auth-token:}") String twilioAuthToken,
            @Value("${vault.sms.from-phone:}") String twilioFromPhone) {
        this.mailSender = mailSender;
        this.mailFrom = mailFrom;
        this.mailUsername = mailUsername;
        this.mailHost = mailHost;
        this.appBaseUrl = appBaseUrl.endsWith("/") ? appBaseUrl.substring(0, appBaseUrl.length() - 1) : appBaseUrl;
        this.twilioAccountSid = twilioAccountSid;
        this.twilioAuthToken = twilioAuthToken;
        this.twilioFromPhone = twilioFromPhone;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean isEmailConfigured() {
        return mailSender != null && StringUtils.hasText(mailUsername) && !"localhost".equalsIgnoreCase(mailHost);
    }

    public boolean isSmsConfigured() {
        return StringUtils.hasText(twilioAccountSid) && StringUtils.hasText(twilioAuthToken) && StringUtils.hasText(twilioFromPhone);
    }

    public String getMailFrom() {
        return mailFrom;
    }

    public String getAppBaseUrl() {
        return appBaseUrl;
    }

    /**
     * Sends an email via configured SMTP or returns accurate SIMULATED / FAILED status.
     * Never falsely marks a failed attempt as SENT.
     */
    public NotificationResult sendEmailDetailed(String toEmail, String subject, String body) {
        if (!StringUtils.hasText(toEmail)) {
            logger.warn("[NotificationService] Cannot send email: recipient is blank.");
            return NotificationResult.failed("Recipient email address is blank.");
        }

        if (isEmailConfigured() && mailSender != null) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(mailFrom);
                message.setTo(toEmail.trim());
                message.setSubject(subject);
                message.setText(body);

                mailSender.send(message);
                logger.info("[NotificationService] SMTP email sent successfully to {}", toEmail);
                return NotificationResult.sent("Email delivered via SMTP to " + toEmail);
            } catch (Exception e) {
                logger.error("[NotificationService] SMTP delivery failed to {}: {}", toEmail, e.getMessage());
                return NotificationResult.failed("SMTP send failed: " + e.getMessage());
            }
        } else {
            logger.info("[NotificationService] SMTP unconfigured. [SIMULATED EMAIL] To: {} | Subject: '{}' | Body preview: {}",
                    toEmail, subject, body.length() > 100 ? body.substring(0, 100) + "..." : body);
            return NotificationResult.simulated("SMTP credentials unset. Notification recorded in application logs.");
        }
    }

    /**
     * Backward-compatible helper returning boolean delivered.
     */
    public boolean sendEmail(String toEmail, String subject, String body) {
        NotificationResult result = sendEmailDetailed(toEmail, subject, body);
        return result.delivered();
    }

    /**
     * Dispatches an SMS notification via Twilio HTTP API or records a simulated delivery log.
     */
    public boolean sendSms(String toPhone, String messageText) {
        if (!StringUtils.hasText(toPhone)) {
            return false;
        }

        if (isSmsConfigured()) {
            try {
                String auth = twilioAccountSid + ":" + twilioAuthToken;
                String authHeader = "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

                String url = "https://api.twilio.com/2010-04-01/Accounts/" + twilioAccountSid + "/Messages.json";
                String formData = "To=" + URLEncoder.encode(toPhone.trim(), StandardCharsets.UTF_8)
                        + "&From=" + URLEncoder.encode(twilioFromPhone.trim(), StandardCharsets.UTF_8)
                        + "&Body=" + URLEncoder.encode(messageText, StandardCharsets.UTF_8);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", authHeader)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .timeout(Duration.ofSeconds(15))
                        .POST(HttpRequest.BodyPublishers.ofString(formData))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    logger.info("[NotificationService] Twilio SMS dispatched successfully to {}", toPhone);
                    return true;
                } else {
                    logger.error("[NotificationService] Twilio SMS API error response (HTTP {}): {}", response.statusCode(), response.body());
                }
            } catch (Exception e) {
                logger.error("[NotificationService] Twilio SMS delivery failed: {}. Logging message as fallback.", e.getMessage());
            }
        } else {
            logger.info("[NotificationService] Twilio SMS unconfigured. [SIMULATED SMS] To: {} | Message: '{}'",
                    toPhone, messageText);
        }
        return false;
    }

    /**
     * Sends a cryptographically secure, time-limited verification invitation email to a trusted contact.
     */
    public NotificationResult sendContactInvitation(TrustedContact contact, String rawToken, String ownerName) {
        String verificationUrl = appBaseUrl + "/verify-contact?token=" + rawToken;
        String subject = "[LegacyVault] Action Required: Confirm Trusted Contact Invitation from " + ownerName;
        String body = String.format("""
                Hello %s,

                You have been designated as a trusted inheritance contact / beneficiary by %s on LegacyVault.

                To confirm and verify your contact identity, please click the secure link below:
                %s

                IMPORTANT SECURITY NOTICE:
                - This link is single-use and valid for 7 days.
                - Verifying your email confirms your identity as a trusted contact.
                - VERIFICATION DOES NOT GRANT ACCESS TO ANY ASSETS.
                - All digital assets remain strictly locked and AES-256 encrypted. Assets can only ever be released after the vault owner's death is formally verified and approved, following their explicit access policies.

                If you did not expect this invitation, you may safely disregard this message.

                Regards,
                LegacyVault Security Team
                """,
                contact.getFullName(),
                ownerName,
                verificationUrl
        );

        return sendEmailDetailed(contact.getEmail(), subject, body);
    }

    /**
     * Dispatches multi-channel Dead-Man Switch warning notices to the vault owner.
     */
    public NotificationResult sendInactivityWarningDetailed(User user, int reminderNumber, int daysRemaining) {
        String reminderTitle;
        switch (reminderNumber) {
            case 1 -> reminderTitle = "First Inactivity Notice";
            case 2 -> reminderTitle = "Midpoint Warning";
            case 3 -> reminderTitle = "FINAL NOTICE - Grace Period Expiry Approaching";
            default -> reminderTitle = "Inactivity Reminder #" + reminderNumber;
        }

        String subject = "[LegacyVault Security Alert] " + reminderTitle + " - Heartbeat Required";
        String body = String.format("""
                Dear %s,

                This is an automated safety alert from LegacyVault.
                Your vault account has reached its configured inactivity threshold of %d days without an authenticated check-in.

                Warning Grace Period: %d day(s) remaining before inactivity review status.

                Action Required:
                Please log into your vault at your earliest convenience or click 'Confirm Check-in' on your dashboard to reset your inactivity timer.

                ESTATE SECURITY GUARANTEE:
                Missing a check-in NEVER automatically releases your digital vault assets to anyone.
                Assets remain strictly encrypted with AES-256-GCM. Any emergency access claim requires designated trusted contact identity verification, policy review, and mandatory delays.

                Stay safe,
                LegacyVault Security Operations
                """,
                user.getFullName(),
                user.getInactivityThresholdDays() != null ? user.getInactivityThresholdDays() : 30,
                daysRemaining
        );

        NotificationResult result = sendEmailDetailed(user.getEmail(), subject, body);

        if (StringUtils.hasText(user.getPhoneNumber())) {
            String smsText = String.format(
                    "[LegacyVault Alert] %s: %d days left in grace period. Please log in to confirm your check-in heartbeat.",
                    reminderTitle,
                    daysRemaining
            );
            sendSms(user.getPhoneNumber(), smsText);
        }

        return result;
    }

    public void sendInactivityWarning(User user, int reminderNumber, int daysRemaining) {
        sendInactivityWarningDetailed(user, reminderNumber, daysRemaining);
    }

    /**
     * Alerts the vault owner when a death confirmation claim has been submitted.
     */
    public NotificationResult sendDeathClaimAlertToOwner(User owner, DeathClaim claim, int challengeDays) {
        String subject = "[URGENT SECURITY ALERT] Formal Death Confirmation Claim Submitted for Your Account";
        String body = String.format("""
                URGENT SECURITY NOTICE FOR: %s

                A formal death confirmation claim (Claim #%d) has been submitted for your LegacyVault account by:
                Claimant: %s

                MANDATORY OWNER CHALLENGE WINDOW:
                Your account is currently in a protective challenge window.
                Challenge window deadline: %s (%d days)

                IF YOU ARE ALIVE:
                Log into your LegacyVault account immediately to VETO this claim and reset your account heartbeat.
                Clicking 'Veto Claim' instantly rejects the claim, confirms your active status, and guarantees all vault assets remain locked.

                PROTECTION GUARANTEE:
                No digital assets have been or will be released during the challenge window.
                Even after the window, claims require official manual legal verification of supporting documents.

                LegacyVault Security Operations
                """,
                owner.getFullName(),
                claim.getId(),
                claim.getClaimantEmail(),
                claim.getChallengeWindowExpiresAt(),
                challengeDays
        );

        NotificationResult result = sendEmailDetailed(owner.getEmail(), subject, body);

        if (StringUtils.hasText(owner.getPhoneNumber())) {
            String sms = String.format(
                    "[LegacyVault CRITICAL ALERT] A death claim (#%d) was submitted for your account. Log in immediately to veto this claim.",
                    claim.getId()
            );
            sendSms(owner.getPhoneNumber(), sms);
        }

        return result;
    }

    /**
     * Sends an acknowledgment receipt to the claimant after submitting a death claim.
     */
    public NotificationResult sendClaimSubmissionReceipt(String claimantEmail, User owner, DeathClaim claim) {
        String subject = "[LegacyVault] Confirmation: Death Verification Claim #" + claim.getId() + " Received";
        String body = String.format("""
                Hello,

                This confirms receipt of your formal death confirmation claim (Claim #%d) regarding vault owner %s (%s).

                CURRENT STATUS: %s
                Owner Challenge Window Expires: %s

                WHAT HAPPENS NEXT:
                1. The vault owner has been notified via their registered security channels with an opportunity to challenge/veto the claim if they are alive.
                2. Following the challenge window, an authorized verification reviewer will evaluate the submitted evidence (e.g. death certificate) and documentation.
                3. Stopping check-ins or inactivity is NEVER proof of death. Only approved verification by an authorized verifier can transition the account.
                4. You will receive an email update once the evaluation decision is recorded.

                LegacyVault Verification Operations
                """,
                claim.getId(),
                owner.getFullName(),
                owner.getEmail(),
                claim.getClaimStatus(),
                claim.getChallengeWindowExpiresAt()
        );

        return sendEmailDetailed(claimantEmail, subject, body);
    }

    /**
     * Informs the claimant of the review decision.
     */
    public NotificationResult sendClaimReviewDecision(DeathClaim claim, String decision, String notes) {
        String subject = "[LegacyVault] Formal Decision for Death Claim #" + claim.getId() + ": " + decision;
        String body = String.format("""
                Hello,

                An authorized reviewer has completed evaluation of Death Confirmation Claim #%d.

                OFFICIAL DECISION: %s
                REVIEW JUSTIFICATION:
                %s

                %s

                LegacyVault Compliance & Estate Administration
                """,
                claim.getId(),
                decision,
                notes,
                "APPROVED_DEATH_VERIFIED".equalsIgnoreCase(decision)
                        ? "Asset release policies established by the owner have now been evaluated. Authorized beneficiaries will receive individual notifications for designated assets."
                        : "Assets remain strictly locked and encrypted. If you have additional official documentation to provide, please contact legal verification support."
        );

        return sendEmailDetailed(claim.getClaimantEmail(), subject, body);
    }

    /**
     * Notifies a verified trusted contact that an authorized digital asset has been released to them.
     */
    public NotificationResult sendAssetReleaseNotice(TrustedContact contact, VaultAsset asset, String permissionLevel) {
        String accessUrl = appBaseUrl + "/beneficiary-vault";
        String subject = "[LegacyVault Release Notice] Digital Asset Released: " + asset.getTitle();
        String body = String.format("""
                Dear %s,

                Following verified death confirmation and per the pre-configured estate directives of %s,
                the following digital asset has been released to you:

                Asset Title: %s
                Category: %s
                Permission Granted: %s

                HOW TO ACCESS YOUR ASSET:
                Please log into LegacyVault with your registered email address (%s) to view or download your authorized asset:
                %s

                SECURITY & PRIVACY:
                This release gives you access solely to the specific digital assets explicitly authorized by the deceased owner.
                All unauthorized vault contents remain completely sealed and encrypted.

                LegacyVault Digital Estate Administration
                """,
                contact.getFullName(),
                asset.getUser().getFullName(),
                asset.getTitle(),
                asset.getCategory(),
                permissionLevel,
                contact.getEmail(),
                accessUrl
        );

        return sendEmailDetailed(contact.getEmail(), subject, body);
    }
}
