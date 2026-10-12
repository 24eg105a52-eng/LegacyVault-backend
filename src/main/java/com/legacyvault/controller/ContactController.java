package com.legacyvault.controller;

import com.legacyvault.model.User;
import com.legacyvault.payload.ApiResponse;
import com.legacyvault.payload.ContactRequest;
import com.legacyvault.payload.ContactResponse;
import com.legacyvault.service.ContactService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller managing designated trusted contacts and beneficiaries.
 */
@RestController
@RequestMapping("/api/contacts")
public class ContactController {

    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse> getContacts(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, "Authentication required."));
        }
        List<ContactResponse> contacts = contactService.getContactsForUser(currentUser);
        return ResponseEntity.ok(new ApiResponse(true, "Contacts retrieved successfully.", contacts));
    }

    @PostMapping
    public ResponseEntity<ApiResponse> addContact(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody ContactRequest request) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, "Authentication required."));
        }
        ApiResponse response = contactService.addContact(currentUser, request);
        if (!response.isSuccess()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deleteContact(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, "Authentication required."));
        }
        boolean deleted = contactService.deleteContact(currentUser, id);
        if (!deleted) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse(false, "Contact not found."));
        }
        return ResponseEntity.ok(new ApiResponse(true, "Contact removed successfully."));
    }
}
