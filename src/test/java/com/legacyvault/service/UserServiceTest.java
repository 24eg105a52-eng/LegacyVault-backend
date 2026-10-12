package com.legacyvault.service;

import com.legacyvault.model.AuditLog;
import com.legacyvault.model.CheckInLog;
import com.legacyvault.model.User;
import com.legacyvault.payload.*;
import com.legacyvault.repository.AuditLogRepository;
import com.legacyvault.repository.CheckInLogRepository;
import com.legacyvault.repository.UserRepository;
import com.legacyvault.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private CheckInLogRepository checkInLogRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private UserService userService;

    private RegisterRequest validRegisterRequest;
    private LoginRequest validLoginRequest;
    private User activeUser;

    @BeforeEach
    void setUp() {
        validRegisterRequest = new RegisterRequest("Alice Smith", "alice@example.com", "Password123!");
        validLoginRequest = new LoginRequest("alice@example.com", "Password123!");

        activeUser = new User();
        activeUser.setId(1L);
        activeUser.setFullName("Alice Smith");
        activeUser.setEmail("alice@example.com");
        activeUser.setPasswordHash("$2a$10$hashedPasswordSample");
        activeUser.setRole("ROLE_USER");
        activeUser.setIsActive(true);
        activeUser.setInactivityStatus("ACTIVE");
        activeUser.setInactivityThresholdDays(30);
        activeUser.setGracePeriodDays(14);
        activeUser.setEmergencyDelayHours(168);
        activeUser.setLastHeartbeatAt(LocalDateTime.now().minusDays(1));
    }

    // ==================== Registration Tests ====================

    @Test
    void registerUser_WhenEmailIsNew_ShouldHashPasswordAndSaveUser() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("$2a$10$hashedPasswordSample");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(1L);
            return u;
        });

        ApiResponse response = userService.registerUser(validRegisterRequest);

        assertTrue(response.isSuccess());
        assertEquals("Registration successful! You can now log in.", response.getMessage());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertEquals("Alice Smith", savedUser.getFullName());
        assertEquals("alice@example.com", savedUser.getEmail());
        assertEquals("$2a$10$hashedPasswordSample", savedUser.getPasswordHash());

        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    void registerUser_WhenEmailAlreadyExists_ShouldReturnErrorWithoutSaving() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        ApiResponse response = userService.registerUser(validRegisterRequest);

        assertFalse(response.isSuccess());
        assertTrue(response.getMessage().contains("already exists"));

        verify(userRepository, never()).save(any(User.class));
        verify(auditLogRepository, never()).save(any(AuditLog.class));
    }

    // ==================== Login Tests ====================

    @Test
    void loginUser_WithValidCredentials_ShouldSucceedAndReturnSignedJwt() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("Password123!", "$2a$10$hashedPasswordSample")).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtUtil.generateToken("alice@example.com", 1L, "ROLE_USER")).thenReturn("header.payload.signature");

        ApiResponse response = userService.loginUser(validLoginRequest);

        assertTrue(response.isSuccess());
        assertTrue(response.getMessage().contains("Login successful"));
        assertNotNull(response.getData());
        assertInstanceOf(LoginResponse.class, response.getData());

        LoginResponse loginData = (LoginResponse) response.getData();
        assertEquals(1L, loginData.getId());
        assertEquals("alice@example.com", loginData.getEmail());
        assertEquals("Alice Smith", loginData.getFullName());
        assertEquals("header.payload.signature", loginData.getToken());
        assertEquals("Bearer", loginData.getTokenType());

        verify(checkInLogRepository).save(any(CheckInLog.class));
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    void loginUser_WithUnknownEmail_ShouldReturnGenericError() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        LoginRequest unknownRequest = new LoginRequest("unknown@example.com", "Password123!");
        ApiResponse response = userService.loginUser(unknownRequest);

        assertFalse(response.isSuccess());
        assertEquals("Invalid email or password.", response.getMessage());

        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void loginUser_WithIncorrectPassword_ShouldReturnGenericError() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("WrongPassword!", "$2a$10$hashedPasswordSample")).thenReturn(false);

        LoginRequest wrongPasswordRequest = new LoginRequest("alice@example.com", "WrongPassword!");
        ApiResponse response = userService.loginUser(wrongPasswordRequest);

        assertFalse(response.isSuccess());
        assertEquals("Invalid email or password.", response.getMessage());

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void loginUser_WithInactiveAccount_ShouldReturnDeactivatedError() {
        activeUser.setIsActive(false);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser));

        ApiResponse response = userService.loginUser(validLoginRequest);

        assertFalse(response.isSuccess());
        assertTrue(response.getMessage().contains("deactivated"));

        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    // ==================== Profile & Password Tests ====================

    @Test
    void updateUserProfile_ShouldUpdateDetailsAndNeverExposePasswordHash() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateProfileRequest request = new UpdateProfileRequest("Alice M. Smith", "+15551234567");
        ApiResponse response = userService.updateUserProfile(activeUser, request);

        assertTrue(response.isSuccess());
        assertNotNull(response.getData());
        assertInstanceOf(UserProfileResponse.class, response.getData());

        UserProfileResponse profile = (UserProfileResponse) response.getData();
        assertEquals("Alice M. Smith", profile.getFullName());
        assertEquals("+15551234567", profile.getPhoneNumber());

        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    void changePassword_WithCorrectCurrentPassword_ShouldUpdateHash() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("OldPassword123!", "$2a$10$hashedPasswordSample")).thenReturn(true);
        when(passwordEncoder.encode("NewSecretPassword456!")).thenReturn("$2a$10$newHashedPasswordSample");

        ChangePasswordRequest request = new ChangePasswordRequest("OldPassword123!", "NewSecretPassword456!");
        ApiResponse response = userService.changePassword(activeUser, request);

        assertTrue(response.isSuccess());
        assertEquals("Password changed successfully.", response.getMessage());

        assertEquals("$2a$10$newHashedPasswordSample", activeUser.getPasswordHash());
        verify(userRepository).save(activeUser);
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    void changePassword_WithWrongCurrentPassword_ShouldReject() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("WrongOldPassword!", "$2a$10$hashedPasswordSample")).thenReturn(false);

        ChangePasswordRequest request = new ChangePasswordRequest("WrongOldPassword!", "NewSecretPassword456!");
        ApiResponse response = userService.changePassword(activeUser, request);

        assertFalse(response.isSuccess());
        assertEquals("Current password does not match.", response.getMessage());

        verify(passwordEncoder, never()).encode(anyString());
    }
}
