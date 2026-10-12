package com.legacyvault.service;

import com.legacyvault.model.AuditLog;
import com.legacyvault.model.GracePeriodReminder;
import com.legacyvault.model.User;
import com.legacyvault.repository.AuditLogRepository;
import com.legacyvault.repository.GracePeriodReminderRepository;
import com.legacyvault.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InactivitySchedulerServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private GracePeriodReminderRepository gracePeriodReminderRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private InactivitySchedulerService schedulerService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(5L);
        user.setEmail("user@example.com");
        user.setFullName("Vault Owner");
        user.setIsActive(true);
        user.setInactivityStatus("ACTIVE");
        user.setInactivityThresholdDays(30);
        user.setGracePeriodDays(14);
    }

    @Test
    void processUserInactivity_WhenInactivityExceedsThreshold_ShouldTransitionToWarningGracePeriodAndSendNotice() {
        // Last heartbeat 35 days ago (threshold is 30)
        LocalDateTime now = LocalDateTime.now();
        user.setLastHeartbeatAt(now.minusDays(35));

        when(gracePeriodReminderRepository.existsByUserIdAndReminderNumberAndStatus(5L, 1, "SENT"))
                .thenReturn(false);

        schedulerService.processUserInactivity(user, now);

        assertEquals("WARNING_GRACE_PERIOD", user.getInactivityStatus());
        verify(userRepository).save(user);
        verify(notificationService).sendInactivityWarning(eq(user), eq(1), eq(14));

        ArgumentCaptor<GracePeriodReminder> captor = ArgumentCaptor.forClass(GracePeriodReminder.class);
        verify(gracePeriodReminderRepository).save(captor.capture());
        GracePeriodReminder reminder = captor.getValue();
        assertEquals(1, reminder.getReminderNumber());
        assertEquals("user@example.com", reminder.getSentToEmail());
        assertEquals("SENT", reminder.getStatus());

        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    void processUserInactivity_WhenWarningAlreadySent_ShouldPreventDuplicateNotice() {
        LocalDateTime now = LocalDateTime.now();
        user.setLastHeartbeatAt(now.minusDays(35));

        when(gracePeriodReminderRepository.existsByUserIdAndReminderNumberAndStatus(5L, 1, "SENT"))
                .thenReturn(true);

        schedulerService.processUserInactivity(user, now);

        assertEquals("WARNING_GRACE_PERIOD", user.getInactivityStatus());
        verify(userRepository).save(user);
        // Notice must NOT be sent again
        verify(notificationService, never()).sendInactivityWarning(any(), anyInt(), anyInt());
    }

    @Test
    void processUserInactivity_WhenGracePeriodExpires_ShouldTransitionToInactivePendingVerificationWithoutReleasingAssets() {
        LocalDateTime now = LocalDateTime.now();
        user.setInactivityStatus("WARNING_GRACE_PERIOD");
        user.setLastHeartbeatAt(now.minusDays(45));

        GracePeriodReminder existingReminder = new GracePeriodReminder();
        existingReminder.setId(101L);
        existingReminder.setUser(user);
        existingReminder.setReminderNumber(1);
        existingReminder.setStatus("SENT");
        // Expired 2 days ago
        existingReminder.setGracePeriodExpiresAt(now.minusDays(2));

        when(gracePeriodReminderRepository.findTopByUserIdOrderByCreatedAtDesc(5L))
                .thenReturn(Optional.of(existingReminder));

        schedulerService.processUserInactivity(user, now);

        assertEquals("INACTIVE_PENDING_VERIFICATION", user.getInactivityStatus());
        assertEquals("EXPIRED_WITHOUT_RESPONSE", existingReminder.getStatus());
        verify(userRepository).save(user);
        verify(notificationService).sendInactivityWarning(eq(user), eq(3), eq(0));
        verify(gracePeriodReminderRepository, atLeastOnce()).save(any(GracePeriodReminder.class));
    }
}
