package com.legacyvault.controller;

import com.legacyvault.payload.ApiResponse;
import com.legacyvault.payload.LoginRequest;
import com.legacyvault.payload.RegisterRequest;
import com.legacyvault.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller exposing authentication, login, and registration endpoints.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Endpoint for new user registration.
     * Accessible publicly without authentication tokens.
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse> registerUser(@Valid @RequestBody RegisterRequest request) {
        ApiResponse response = userService.registerUser(request);

        if (!response.isSuccess()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Endpoint for user authentication / login.
     * Accessible publicly without authentication tokens.
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse> loginUser(@Valid @RequestBody LoginRequest request) {
        ApiResponse response = userService.loginUser(request);

        if (!response.isSuccess()) {
            if (response.getMessage() != null && response.getMessage().contains("deactivated")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        return ResponseEntity.ok(response);
    }
}
