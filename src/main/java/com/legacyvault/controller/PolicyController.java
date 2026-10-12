package com.legacyvault.controller;

import com.legacyvault.model.User;
import com.legacyvault.payload.AccessPolicyRequest;
import com.legacyvault.payload.AccessPolicyResponse;
import com.legacyvault.payload.ApiResponse;
import com.legacyvault.service.PolicyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller managing asset-specific release policies configured by vault owners.
 */
@RestController
@RequestMapping("/api/policies")
public class PolicyController {

    private final PolicyService policyService;

    public PolicyController(PolicyService policyService) {
        this.policyService = policyService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse> getPolicies(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, "Authentication required."));
        }
        List<AccessPolicyResponse> policies = policyService.getPoliciesForOwner(currentUser);
        return ResponseEntity.ok(new ApiResponse(true, "Policies retrieved successfully.", policies));
    }

    @PostMapping
    public ResponseEntity<ApiResponse> savePolicy(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody AccessPolicyRequest request) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, "Authentication required."));
        }
        ApiResponse response = policyService.savePolicy(currentUser, request);
        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deletePolicy(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, "Authentication required."));
        }
        boolean deleted = policyService.deletePolicy(currentUser, id);
        if (!deleted) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse(false, "Policy not found."));
        }
        return ResponseEntity.ok(new ApiResponse(true, "Release policy deleted successfully."));
    }
}
