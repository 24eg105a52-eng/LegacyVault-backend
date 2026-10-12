package com.legacyvault.service;

import com.legacyvault.model.AuditLog;
import com.legacyvault.model.GracePeriodReminder;
import com.legacyvault.model.User;
import com.legacyvault.repository.AuditLogRepository;
import com.legacyvault.repository.GracePeriodReminderRepository;
import com.legacyvault.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Background watchdog service executing automated Inactivity & Dead-Man Switch checks.
 *
 * Implements safe, non-immediate protocol transitions:
 * 1. Overdue heartbeat moves account to WARNING_GRACE_PERIOD with multi-channel warnings.
 * 2. Unacknowledged warnings move account to INACTIVE_PENDING_VERIFICATION.
 * 3. ZERO FALSE RELEASES: Inactivity never auto-releases vault assets.
 */
@Service
@ConditionalOnProperty(name = "vault.inactivity.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class InactivitySchedulerService {

    private static final Logger logger = LoggerFactory.getLogger(InactivitySchedulerService.class);

    private final UserRepository userRepository;
    private final GracePeriodReminderRepository gracePeriodReminderRepository;
    private final NotificationService notificationService;
    private final AuditLogRepository auditLogRepository;

    public InactivitySchedulerService(
            UserRepository userRepository,
            GracePeriodReminderRepository gracePeriodReminderRepository,
            NotificationService notificationService,
            AuditLogRepository auditLogRepository) {
        this.userRepository = userRepository;
        this.gracePeriodReminderRepository = gracePeriodReminderRepository;
        this.notificationService = notificationService;
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Periodically inspects user heartbeats and manages multi-stage grace periods.
     */
    @Scheduled(fixedRateString = "${vault.inactivity.scheduler.rate-ms:3600000}")
    @Transactional
    public void evaluateInactivityDeadManSwitch() {
        logger.debug("[InactivityScheduler] Running scheduled heartbeat watchdog sweep...");
        List<User> activeUsers = userRepository.findByIsActiveTrue();
        LocalDateTime now = LocalDateTime.now();

        for (User user : activeUsers) {
            try {
                processUserInactivity(user, now);
            } catch (Exception e) {
                logger.error("[InactivityScheduler] Error processing inactivity for user {}: {}", user.getEmail(), e.getMessage());
            }
        }
    }

    public void processUserInactivity(User user, LocalDateTime now) {
        LocalDateTime lastHeartbeat = user.getLastHeartbeatAt() != null ? user.getLastHeartbeatAt() : user.getCreatedAt();
        if (lastHeartbeat == null) {
            lastHeartbeat = now;
        }

        int thresholdDays = user.getInactivityThresholdDays() != null ? user.getInactivityThresholdDays() : 30;
        int graceDays = user.getGracePeriodDays() != null ? user.getGracePeriodDays() : 14;

        long daysInactive = Duration.between(lastHeartbeat, now).toDays();

        // 1. User reached threshold while in ACTIVE state -> Move to WARNING_GRACE_PERIOD
        if (daysInactive >= thresholdDays && "ACTIVE".equalsIgnoreCase(user.getInactivityStatus())) {
            user.setInactivityStatus("WARNING_GRACE_PERIOD");
            userRepository.save(user);

            boolean reminderExists = gracePeriodReminderRepository
                    .existsByUserIdAndReminderNumberAndStatus(user.getId(), 1, "SENT");

            if (!reminderExists) {
                LocalDateTime expiresAt = now.plusDays(graceDays);
                notificationService.sendInactivityWarning(user, 1, graceDays);

                GracePeriodReminder reminder = new GracePeriodReminder();
                reminder.setUser(user);
                reminder.setReminderNumber(1);
                reminder.setSentToEmail(user.getEmail());
                reminder.setGracePeriodExpiresAt(expiresAt);
                reminder.setStatus("SENT");
                gracePeriodReminderRepository.save(reminder);

                recordAudit(user, "INACTIVITY_WARNING_TRIGGERED",
                        "Heartbeat threshold reached (" + daysInactive + " days). Sent Grace Period Warning #1.");
            }
        }
        // 2. User is currently in WARNING_GRACE_PERIOD
        else if ("WARNING_GRACE_PERIOD".equalsIgnoreCase(user.getInactivityStatus())) {
            Optional<GracePeriodReminder> latestReminderOpt = gracePeriodReminderRepository
                    .findTopByUserIdOrderByCreatedAtDesc(user.getId());

            if (latestReminderOpt.isPresent()) {
                GracePeriodReminder latestReminder = latestReminderOpt.get();
                LocalDateTime expiresAt = latestReminder.getGracePeriodExpiresAt();

                // Check if grace period has expired without check-in response
                if (now.isAfter(expiresAt)) {
                    user.setInactivityStatus("INACTIVE_PENDING_VERIFICATION");
                    userRepository.save(user);

                    latestReminder.setStatus("EXPIRED_WITHOUT_RESPONSE");
                    gracePeriodReminderRepository.save(latestReminder);

                    // Send final notice
                    notificationService.sendInactivityWarning(user, 3, 0);

                    GracePeriodReminder finalReminder = new GracePeriodReminder();
                    finalReminder.setUser(user);
                    finalReminder.setReminderNumber(3);
                    finalReminder.setSentToEmail(user.getEmail());
                    finalReminder.setGracePeriodExpiresAt(now);
                    finalReminder.setStatus("SENT");
                    gracePeriodReminderRepository.save(finalReminder);

                    recordAudit(user, "GRACE_PERIOD_EXPIRED",
                            "Grace period expired without check-in. Status set to INACTIVE_PENDING_VERIFICATION. Assets remain locked.");
                } else {
                    // Still in grace period: Check if midpoint reminder #2 should be sent
                    long daysRemaining = Duration.between(now, expiresAt).toDays();
                    boolean reminder2Sent = gracePeriodReminderRepository
                            .existsByUserIdAndReminderNumberAndStatus(user.getId(), 2, "SENT");

                    if (daysRemaining <= (graceDays / 2) && !reminder2Sent) {
                        notificationService.sendInactivityWarning(user, 2, (int) Math.max(1, daysRemaining));

                        GracePeriodReminder midReminder = new GracePeriodReminder();
                        midReminder.setUser(user);
                        midReminder.setReminderNumber(2);
                        midReminder.setSentToEmail(user.getEmail());
                        midReminder.setGracePeriodExpiresAt(expiresAt);
                        midReminder.setStatus("SENT");
                        gracePeriodReminderRepository.save(midReminder);

                        recordAudit(user, "INACTIVITY_MIDPOINT_WARNING",
                                "Sent Grace Period Midpoint Warning #2 (" + daysRemaining + " days remaining).");
                    }
                }
            }
        }
    }

    private void recordAudit(User user, String action, String details) {
        AuditLog audit = new AuditLog();
        audit.setUser(user);
        audit.setActorType("SYSTEM_SCHEDULER");
        audit.setActorIdentifier("SYSTEM_DEAD_MAN_WATCHDOG");
        audit.setAction(action);
        audit.setResourceType("USER_INACTIVITY_MONITOR");
        audit.setResourceId(String.valueOf(user.getId()));
        audit.setDetails(details);
        auditLogRepository.save(audit);
    }
}
