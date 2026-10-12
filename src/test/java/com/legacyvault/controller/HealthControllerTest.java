package com.legacyvault.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HealthControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        HealthController healthController = new HealthController();
        mockMvc = MockMvcBuilders.standaloneSetup(healthController).build();
    }

    @Test
    void checkHealth_ApiHealthEndpoint_ShouldReturn200Up() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("LegacyVault Backend API"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void checkHealth_HealthEndpoint_ShouldReturn200Up() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("LegacyVault Backend API"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }
}
