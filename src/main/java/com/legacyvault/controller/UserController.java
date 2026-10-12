package com.legacyvault.controller;

import com.legacyvault.model.User;
import com.legacyvault.payload.ApiResponse;
import com.legacyvault.payload.ChangePasswordRequest;
import com.legacyvault.payload.UpdateProfileRequest;
import com.legacyvault.payload.UserProfileResponse;
import com.legacyvault.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Controller handling authenticated user profile operations, password changes, and check-ins.
 * Identity is determined strictly by the validated JWT AuthenticationPrincipal.
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse> getProfile(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, "Authentication required."));
        }
        UserProfileResponse profile = userService.getUserProfile(currentUser);
        return ResponseEntity.ok(new ApiResponse(true, "Profile retrieved successfully.", profile));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse> updateProfile(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody UpdateProfileRequest request) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, "Authentication required."));
        }
        ApiResponse response = userService.updateUserProfile(currentUser, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse> changePassword(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody ChangePasswordRequest request) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, "Authentication required."));
        }
        ApiResponse response = userService.changePassword(currentUser, request);
        if (!response.isSuccess()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<ApiResponse> recordHeartbeat(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, "Authentication required."));
        }
        ApiResponse response = userService.recordHeartbeat(currentUser);
        return ResponseEntity.ok(response);
    }
}
