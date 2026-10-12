package com.legacyvault.payload;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO providing real computed metrics and recent activity for the user's dashboard.
 */
public class DashboardStatsResponse {

    public record RecentActivityItem(Long id, String action, String details, LocalDateTime timestamp) {}

    private long assetCount;
    private long contactCount;
    private long pendingEmergencyRequestsCount;
    private String checkInStatus;
    private int daysUntilNextCheckIn;
    private LocalDateTime lastHeartbeatAt;
    private List<RecentActivityItem> recentActivities;

    public DashboardStatsResponse() {
    }

    public DashboardStatsResponse(long assetCount, long contactCount, long pendingEmergencyRequestsCount,
                                  String checkInStatus, int daysUntilNextCheckIn,
                                  LocalDateTime lastHeartbeatAt, List<RecentActivityItem> recentActivities) {
        this.assetCount = assetCount;
        this.contactCount = contactCount;
        this.pendingEmergencyRequestsCount = pendingEmergencyRequestsCount;
        this.checkInStatus = checkInStatus;
        this.daysUntilNextCheckIn = daysUntilNextCheckIn;
        this.lastHeartbeatAt = lastHeartbeatAt;
        this.recentActivities = recentActivities;
    }

    public long getAssetCount() {
        return assetCount;
    }

    public void setAssetCount(long assetCount) {
        this.assetCount = assetCount;
    }

    public long getContactCount() {
        return contactCount;
    }

    public void setContactCount(long contactCount) {
        this.contactCount = contactCount;
    }

    public long getPendingEmergencyRequestsCount() {
        return pendingEmergencyRequestsCount;
    }

    public void setPendingEmergencyRequestsCount(long pendingEmergencyRequestsCount) {
        this.pendingEmergencyRequestsCount = pendingEmergencyRequestsCount;
    }

    public String getCheckInStatus() {
        return checkInStatus;
    }

    public void setCheckInStatus(String checkInStatus) {
        this.checkInStatus = checkInStatus;
    }

    public int getDaysUntilNextCheckIn() {
        return daysUntilNextCheckIn;
    }

    public void setDaysUntilNextCheckIn(int daysUntilNextCheckIn) {
        this.daysUntilNextCheckIn = daysUntilNextCheckIn;
    }

    public LocalDateTime getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public void setLastHeartbeatAt(LocalDateTime lastHeartbeatAt) {
        this.lastHeartbeatAt = lastHeartbeatAt;
    }

    public List<RecentActivityItem> getRecentActivities() {
        return recentActivities;
    }

    public void setRecentActivities(List<RecentActivityItem> recentActivities) {
        this.recentActivities = recentActivities;
    }
}
