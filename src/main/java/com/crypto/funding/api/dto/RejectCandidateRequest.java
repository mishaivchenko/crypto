package com.crypto.funding.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectCandidateRequest(
    @NotBlank @Size(max = 500) String reviewNotes
)
{
}
