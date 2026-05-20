package com.crypto.funding.api.dto;

import jakarta.validation.constraints.Size;

public record RejectCandidateRequest(
    @Size(max = 500) String reviewNotes
)
{
}
