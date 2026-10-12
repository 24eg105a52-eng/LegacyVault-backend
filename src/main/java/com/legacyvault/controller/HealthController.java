package com.legacyvault.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Public zero-authentication health check endpoint for cloud hosting platforms (e.g., Render, Kubernetes).
 * Provides a lightweight HTTP 200 response to confirm service liveness without exposing internal credentials.
 */
@RestController
public class HealthController {

    @GetMapping({"/api/health", "/health"})
    public ResponseEntity<Map<String, Object>> checkHealth() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "LegacyVault Backend API",
                "timestamp", Instant.now().toString()
        ));
    }
}
