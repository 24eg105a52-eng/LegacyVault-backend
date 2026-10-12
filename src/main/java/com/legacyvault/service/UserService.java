package com.legacyvault.service;

import com.legacyvault.model.AuditLog;
import com.legacyvault.model.CheckInLog;
import com.legacyvault.model.GracePeriodReminder;
import com.legacyvault.model.User;
import com.legacyvault.payload.*;
import com.legacyvault.repository.AuditLogRepository;
import com.legacyvault.repository.CheckInLogRepository;
import com.legacyvault.repository.GracePeriodReminderRepository;
import com.legacyvault.repository.UserRepository;
import com.legacyvault.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service managing user lifecycle, authentication, profile management, and heartbeat check-ins.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final CheckInLogRepository checkInLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final GracePeriodReminderRepository gracePeriodReminderRepository;

    @Autowired
    public UserService(UserRepository userRepository,
                       AuditLogRepository auditLogRepository,
                       CheckInLogRepository checkInLogRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       @Autowired(required = false) GracePeriodReminderRepository gracePeriodReminderRepository) {
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.checkInLogRepository = checkInLogRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.gracePeriodReminderRepository = gracePeriodReminderRepository;
    }

    /**
     * Registers a new vault owner account.
     */
    @Transactional
    public ApiResponse registerUser(RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            return new ApiResponse(false, "An account with email '" + normalizedEmail + "' already exists.");
        }

        String hashedPassword = passwordEncoder.encode(request.getPassword());

        User user = new User();
        user.setFullName(request.getFullName().trim());
        user.setEmail(normalizedEmail);
        user.setPasswordHash(hashedPassword);
        user.setRole("ROLE_USER");
        user.setIsActive(true);
        user.setInactivityStatus("ACTIVE");
        user.setInactivityThresholdDays(30);
        user.setGracePeriodDays(14);
        user.setEmergencyDelayHours(168);
        user.setLastHeartbeatAt(LocalDateTime.now());

        User savedUser = userRepository.save(user);

        recordAuditLog(savedUser, savedUser.getEmail(), "USER_REGISTERED", "New vault user registered: " + savedUser.getFullName());

        return new ApiResponse(true, "Registration successful! You can now log in.");
    }

    /**
     * Authenticates a user and returns a cryptographically signed JWT token with user details.
     */
    @Transactional
    public ApiResponse loginUser(LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        Optional<User> userOptional = userRepository.findByEmail(normalizedEmail);
        if (userOptional.isEmpty()) {
            recordAuditLog(null, normalizedEmail, "AUTH_LOGIN_FAILED", "Failed login: email not found");
            return new ApiResponse(false, "Invalid email or password.");
        }

        User user = userOptional.get();

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            recordAuditLog(user, user.getEmail(), "AUTH_LOGIN_FAILED", "Failed login: account is deactivated");
            return new ApiResponse(false, "Your account is deactivated. Please contact support.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            recordAuditLog(user, user.getEmail(), "AUTH_LOGIN_FAILED", "Failed login: incorrect password");
            return new ApiResponse(false, "Invalid email or password.");
        }

        // Update heartbeat timestamp on login and reset grace period warning if present
        user.setLastHeartbeatAt(LocalDateTime.now());
        if ("WARNING_GRACE_PERIOD".equals(user.getInactivityStatus())) {
            user.setInactivityStatus("ACTIVE");
        }
        User updatedUser = userRepository.save(user);

        if (gracePeriodReminderRepository != null) {
            List<GracePeriodReminder> pending = gracePeriodReminderRepository.findByUserIdAndStatus(updatedUser.getId(), "SENT");
            for (GracePeriodReminder r : pending) {
                r.setStatus("ACKNOWLEDGED_BY_USER");
                gracePeriodReminderRepository.save(r);
            }
        }

        // Record check-in log
        recordCheckIn(updatedUser, "SESSION_LOGIN");

        recordAuditLog(updatedUser, updatedUser.getEmail(), "AUTH_LOGIN_SUCCESS", "User authenticated successfully");

        // Generate signed JWT
        String token = jwtUtil.generateToken(updatedUser.getEmail(), updatedUser.getId(), updatedUser.getRole());

        LoginResponse loginResponse = new LoginResponse(
                updatedUser.getId(),
                updatedUser.getEmail(),
                updatedUser.getFullName(),
                updatedUser.getRole(),
                updatedUser.getInactivityStatus(),
                token,
                "Bearer"
        );

        return new ApiResponse(true, "Login successful! Welcome back, " + updatedUser.getFullName() + ".", loginResponse);
    }

    /**
     * Retrieves the safe profile representation of the authenticated user.
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(User user) {
        // Reload from repository to ensure fresh data
        User freshUser = userRepository.findById(user.getId()).orElse(user);

        return new UserProfileResponse(
                freshUser.getId(),
                freshUser.getEmail(),
                freshUser.getFullName(),
                freshUser.getPhoneNumber(),
                freshUser.getRole(),
                freshUser.getInactivityStatus(),
                freshUser.getInactivityThresholdDays(),
                freshUser.getGracePeriodDays(),
                freshUser.getEmergencyDelayHours(),
                freshUser.getLastHeartbeatAt(),
                freshUser.getCreatedAt()
        );
    }

    /**
     * Updates full name and phone number for the authenticated user.
     */
    @Transactional
    public ApiResponse updateUserProfile(User user, UpdateProfileRequest request) {
        User existing = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        existing.setFullName(request.getFullName().trim());
        existing.setPhoneNumber(request.getPhoneNumber() != null ? request.getPhoneNumber().trim() : null);

        User saved = userRepository.save(existing);
        recordAuditLog(saved, saved.getEmail(), "PROFILE_UPDATED", "Updated profile details: " + saved.getFullName());

        return new ApiResponse(true, "Profile updated successfully.", getUserProfile(saved));
    }

    /**
     * Changes password after verifying the current password against BCrypt hash.
     */
    @Transactional
    public ApiResponse changePassword(User user, ChangePasswordRequest request) {
        User existing = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), existing.getPasswordHash())) {
            recordAuditLog(existing, existing.getEmail(), "PASSWORD_CHANGE_FAILED", "Failed attempt: current password did not match");
            return new ApiResponse(false, "Current password does not match.");
        }

        existing.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(existing);

        recordAuditLog(existing, existing.getEmail(), "PASSWORD_CHANGED", "Password was changed successfully");
        return new ApiResponse(true, "Password changed successfully.");
    }

    /**
     * Records a manual heartbeat check-in ping, updating the user's inactivity countdown.
     */
    @Transactional
    public ApiResponse recordHeartbeat(User user) {
        User existing = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        existing.setLastHeartbeatAt(LocalDateTime.now());
        if ("WARNING_GRACE_PERIOD".equals(existing.getInactivityStatus())) {
            existing.setInactivityStatus("ACTIVE");
        }
        User saved = userRepository.save(existing);

        if (gracePeriodReminderRepository != null) {
            List<GracePeriodReminder> pending = gracePeriodReminderRepository.findByUserIdAndStatus(saved.getId(), "SENT");
            for (GracePeriodReminder r : pending) {
                r.setStatus("ACKNOWLEDGED_BY_USER");
                gracePeriodReminderRepository.save(r);
            }
        }

        recordCheckIn(saved, "MANUAL_PORTAL_PING");
        recordAuditLog(saved, saved.getEmail(), "HEARTBEAT_RECORDED", "Manual check-in heartbeat confirmed");

        return new ApiResponse(true, "Heartbeat check-in confirmed successfully.", getUserProfile(saved));
    }

    private void recordCheckIn(User user, String checkInType) {
        CheckInLog checkIn = new CheckInLog();
        checkIn.setUser(user);
        checkIn.setCheckInType(checkInType);
        checkIn.setRecordedAt(LocalDateTime.now());
        checkInLogRepository.save(checkIn);
    }

    private void recordAuditLog(User user, String actorIdentifier, String action, String details) {
        AuditLog auditLog = new AuditLog();
        auditLog.setUser(user);
        auditLog.setActorType("VAULT_OWNER");
        auditLog.setActorIdentifier(actorIdentifier);
        auditLog.setAction(action);
        auditLog.setResourceType("USER");
        if (user != null && user.getId() != null) {
            auditLog.setResourceId(String.valueOf(user.getId()));
        }
        auditLog.setDetails(details);
        auditLogRepository.save(auditLog);
    }
}
