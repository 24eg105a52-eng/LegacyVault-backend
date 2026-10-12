package com.legacyvault.controller;

import com.legacyvault.model.AuditLog;
import com.legacyvault.model.User;
import com.legacyvault.payload.ApiResponse;
import com.legacyvault.payload.DashboardStatsResponse;
import com.legacyvault.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Controller computing real-time dashboard metrics and recent audit logs
 * exclusively for the authenticated user.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final VaultAssetRepository vaultAssetRepository;
    private final TrustedContactRepository trustedContactRepository;
    private final EmergencyAccessRequestRepository emergencyAccessRequestRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public DashboardController(VaultAssetRepository vaultAssetRepository,
                               TrustedContactRepository trustedContactRepository,
                               EmergencyAccessRequestRepository emergencyAccessRequestRepository,
                               AuditLogRepository auditLogRepository,
                               UserRepository userRepository) {
        this.vaultAssetRepository = vaultAssetRepository;
        this.trustedContactRepository = trustedContactRepository;
        this.emergencyAccessRequestRepository = emergencyAccessRequestRepository;
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse> getDashboardStats(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, "Authentication required."));
        }

        // Fetch fresh user data from database
        User user = userRepository.findById(currentUser.getId()).orElse(currentUser);

        long assetCount = vaultAssetRepository.countByUserIdAndIsActiveTrue(user.getId());
        long contactCount = trustedContactRepository.countByUserId(user.getId());
        long pendingRequests = emergencyAccessRequestRepository
                .countByUserIdAndRequestStatus(user.getId(), "PENDING_OWNER_REVIEW");

        // Compute heartbeat status
        int thresholdDays = user.getInactivityThresholdDays() != null ? user.getInactivityThresholdDays() : 30;
        int daysUntilNextCheckIn = thresholdDays;
        String status = "Healthy";

        if (user.getLastHeartbeatAt() != null) {
            long daysPassed = ChronoUnit.DAYS.between(user.getLastHeartbeatAt(), LocalDateTime.now());
            daysUntilNextCheckIn = Math.max(0, thresholdDays - (int) daysPassed);
            if (daysUntilNextCheckIn == 0) {
                status = "Grace Period Notice";
            } else if (daysUntilNextCheckIn <= 7) {
                status = "Check-in Approaching";
            } else {
                status = "Healthy";
            }
        }

        List<AuditLog> auditLogs = auditLogRepository.findTop10ByUserIdOrderByCreatedAtDesc(user.getId());
        List<DashboardStatsResponse.RecentActivityItem> recentActivities = auditLogs.stream()
                .map(log -> new DashboardStatsResponse.RecentActivityItem(
                        log.getId(),
                        log.getAction(),
                        log.getDetails(),
                        log.getCreatedAt()
                ))
                .toList();

        DashboardStatsResponse stats = new DashboardStatsResponse(
                assetCount,
                contactCount,
                pendingRequests,
                status,
                daysUntilNextCheckIn,
                user.getLastHeartbeatAt(),
                recentActivities
        );

        return ResponseEntity.ok(new ApiResponse(true, "Dashboard statistics retrieved.", stats));
    }
}
