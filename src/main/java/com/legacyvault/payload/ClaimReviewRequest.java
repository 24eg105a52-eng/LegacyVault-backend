package com.legacyvault.payload;

import jakarta.validation.constraints.NotBlank;

public class ClaimReviewRequest {

    @NotBlank(message = "Review decision is required (APPROVED or REJECTED)")
    private String decision;

    @NotBlank(message = "Review justification notes are mandatory")
    private String reviewNotes;

    public ClaimReviewRequest() {
    }

    public ClaimReviewRequest(String decision, String reviewNotes) {
        this.decision = decision;
        this.reviewNotes = reviewNotes;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getReviewNotes() {
        return reviewNotes;
    }

    public void setReviewNotes(String reviewNotes) {
        this.reviewNotes = reviewNotes;
    }
}
