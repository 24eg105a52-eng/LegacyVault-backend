package com.legacyvault.controller;

import com.legacyvault.model.EmergencyAccessRequest;
import com.legacyvault.model.GracePeriodReminder;
import com.legacyvault.model.User;
import com.legacyvault.payload.ApiResponse;
import com.legacyvault.repository.EmergencyAccessRequestRepository;
import com.legacyvault.repository.GracePeriodReminderRepository;
import com.legacyvault.repository.UserRepository;
import com.legacyvault.service.NotificationService;
import com.legacyvault.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller handling emergency access monitoring, manual heartbeat pings,
 * and notification delivery verification.
 */
@RestController
@RequestMapping("/api/emergency-access")
public class EmergencyAccessController {

    private final EmergencyAccessRequestRepository emergencyAccessRequestRepository;
    private final GracePeriodReminderRepository gracePeriodReminderRepository;
    private final NotificationService notificationService;
    private final UserService userService;
    private final UserRepository userRepository;

    public EmergencyAccessController(EmergencyAccessRequestRepository emergencyAccessRequestRepository,
                                     GracePeriodReminderRepository gracePeriodReminderRepository,
                                     NotificationService notificationService,
                                     UserService userService,
                                     UserRepository userRepository) {
        this.emergencyAccessRequestRepository = emergencyAccessRequestRepository;
        this.gracePeriodReminderRepository = gracePeriodReminderRepository;
        this.notificationService = notificationService;
        this.userService = userService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<ApiResponse> getEmergencyStatus(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, "Authentication required."));
        }

        User user = userRepository.findById(currentUser.getId()).orElse(currentUser);
        List<EmergencyAccessRequest> requests =
                emergencyAccessRequestRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        List<GracePeriodReminder> reminders =
                gracePeriodReminderRepository.findByUserIdOrderByCreatedAtDesc(user.getId());

        Map<String, Object> data = new HashMap<>();
        data.put("inactivityStatus", user.getInactivityStatus());
        data.put("inactivityThresholdDays", user.getInactivityThresholdDays());
        data.put("gracePeriodDays", user.getGracePeriodDays());
        data.put("emergencyDelayHours", user.getEmergencyDelayHours());
        data.put("lastHeartbeatAt", user.getLastHeartbeatAt());
        data.put("requests", requests);
        data.put("reminders", reminders);
        data.put("isEmailConfigured", notificationService.isEmailConfigured());
        data.put("isSmsConfigured", notificationService.isSmsConfigured());
        data.put("mailFrom", notificationService.getMailFrom());

        return ResponseEntity.ok(new ApiResponse(true, "Emergency status retrieved.", data));
    }

    @PostMapping("/test-email")
    public ResponseEntity<ApiResponse> sendTestEmail(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, "Authentication required."));
        }

        User user = userRepository.findById(currentUser.getId()).orElse(currentUser);
        String subject = "[LegacyVault] Test Notification Delivery";
        String body = "Hello " + user.getFullName() + ",\n\n" +
                "This is a test notification confirming that SMTP email alerts are active for your LegacyVault account.\n\n" +
                "LegacyVault Security Team";

        boolean sent = notificationService.sendEmail(user.getEmail(), subject, body);
        if (sent) {
            return ResponseEntity.ok(new ApiResponse(true, "Test email dispatched to " + user.getEmail() + " via SMTP."));
        } else {
            return ResponseEntity.ok(new ApiResponse(true, "SMTP credentials unset or unreached. Test email was recorded in system logs (Simulated Dispatch)."));
        }
    }

    @PostMapping("/test-sms")
    public ResponseEntity<ApiResponse> sendTestSms(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, "Authentication required."));
        }

        User user = userRepository.findById(currentUser.getId()).orElse(currentUser);
        if (user.getPhoneNumber() == null || user.getPhoneNumber().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Please set a valid phone number in your Profile first."));
        }

        String message = "[LegacyVault] Test SMS: Notification alerts are active for " + user.getFullName() + ".";
        boolean sent = notificationService.sendSms(user.getPhoneNumber(), message);
        if (sent) {
            return ResponseEntity.ok(new ApiResponse(true, "Test SMS dispatched to " + user.getPhoneNumber() + " via Twilio."));
        } else {
            return ResponseEntity.ok(new ApiResponse(true, "Twilio SMS credentials unset or unreached. Test SMS was recorded in system logs (Simulated Dispatch)."));
        }
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<ApiResponse> confirmHeartbeat(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, "Authentication required."));
        }
        ApiResponse response = userService.recordHeartbeat(currentUser);
        return ResponseEntity.ok(response);
    }
}
