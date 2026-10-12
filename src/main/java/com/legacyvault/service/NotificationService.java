package com.legacyvault.service;

import com.legacyvault.model.User;
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
 * with safe fallbacks and logging when credentials are unset or services are offline.
 */
@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    private final JavaMailSender mailSender;
    private final String mailFrom;
    private final String mailUsername;
    private final String mailHost;

    private final String twilioAccountSid;
    private final String twilioAuthToken;
    private final String twilioFromPhone;

    private final HttpClient httpClient;

    public NotificationService(
            @Autowired(required = false) JavaMailSender mailSender,
            @Value("${vault.mail.from:noreply@legacyvault.local}") String mailFrom,
            @Value("${spring.mail.username:}") String mailUsername,
            @Value("${spring.mail.host:localhost}") String mailHost,
            @Value("${vault.sms.account-sid:}") String twilioAccountSid,
            @Value("${vault.sms.auth-token:}") String twilioAuthToken,
            @Value("${vault.sms.from-phone:}") String twilioFromPhone) {
        this.mailSender = mailSender;
        this.mailFrom = mailFrom;
        this.mailUsername = mailUsername;
        this.mailHost = mailHost;
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

    /**
     * Sends an email via configured SMTP (e.g. Gmail SMTP) or records a simulated delivery log.
     */
    public boolean sendEmail(String toEmail, String subject, String body) {
        if (!StringUtils.hasText(toEmail)) {
            logger.warn("[NotificationService] Cannot send email: recipient is blank.");
            return false;
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
                return true;
            } catch (Exception e) {
                logger.error("[NotificationService] SMTP send failure to {}: {}. Logging message as fallback.", toEmail, e.getMessage());
            }
        } else {
            logger.info("[NotificationService] SMTP credentials not fully configured. [FALLBACK DISPATCH] To: {} | Subject: '{}' | Content: {}",
                    toEmail, subject, body.replace("\n", " "));
        }
        return false;
    }

    /**
     * Sends an SMS notification via Twilio HTTP API or records a simulated delivery log.
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
            logger.info("[NotificationService] Twilio SMS credentials not configured. [FALLBACK SMS] To: {} | Message: '{}'",
                    toPhone, messageText);
        }
        return false;
    }

    /**
     * Dispatches multi-channel Dead-Man Switch warning notices to the vault owner.
     */
    public void sendInactivityWarning(User user, int reminderNumber, int daysRemaining) {
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

        sendEmail(user.getEmail(), subject, body);

        if (StringUtils.hasText(user.getPhoneNumber())) {
            String smsText = String.format(
                    "[LegacyVault Alert] %s: %d days left in grace period. Please log in to confirm your check-in heartbeat.",
                    reminderTitle,
                    daysRemaining
            );
            sendSms(user.getPhoneNumber(), smsText);
        }
    }
}
