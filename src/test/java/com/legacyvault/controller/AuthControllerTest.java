package com.legacyvault.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legacyvault.config.GlobalExceptionHandler;
import com.legacyvault.payload.ApiResponse;
import com.legacyvault.payload.LoginRequest;
import com.legacyvault.payload.LoginResponse;
import com.legacyvault.payload.RegisterRequest;
import com.legacyvault.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private UserService userService;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void registerUser_WhenValid_ShouldReturn201Created() throws Exception {
        RegisterRequest request = new RegisterRequest("Jane Doe", "jane@example.com", "Password123!");
        when(userService.registerUser(any(RegisterRequest.class)))
                .thenReturn(new ApiResponse(true, "Registration successful!"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Registration successful!"));
    }

    @Test
    void loginUser_WhenCredentialsValid_ShouldReturn200Ok() throws Exception {
        LoginRequest request = new LoginRequest("jane@example.com", "Password123!");
        LoginResponse responseData = new LoginResponse(1L, "jane@example.com", "Jane Doe", "ROLE_USER", "ACTIVE");
        when(userService.loginUser(any(LoginRequest.class)))
                .thenReturn(new ApiResponse(true, "Login successful!", responseData));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("jane@example.com"))
                .andExpect(jsonPath("$.data.fullName").value("Jane Doe"));
    }

    @Test
    void loginUser_WhenCredentialsInvalid_ShouldReturn401Unauthorized() throws Exception {
        LoginRequest request = new LoginRequest("jane@example.com", "WrongPassword!");
        when(userService.loginUser(any(LoginRequest.class)))
                .thenReturn(new ApiResponse(false, "Invalid email or password."));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    void loginUser_WhenAccountDeactivated_ShouldReturn403Forbidden() throws Exception {
        LoginRequest request = new LoginRequest("jane@example.com", "Password123!");
        when(userService.loginUser(any(LoginRequest.class)))
                .thenReturn(new ApiResponse(false, "Your account is deactivated. Please contact support."));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Your account is deactivated. Please contact support."));
    }

    @Test
    void loginUser_WhenValidationFails_ShouldReturn400BadRequest() throws Exception {
        LoginRequest request = new LoginRequest("", ""); // Blank email and password

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
