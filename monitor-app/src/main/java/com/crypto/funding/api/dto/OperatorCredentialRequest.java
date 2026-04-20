package com.crypto.funding.api.dto;

import jakarta.validation.constraints.NotBlank;

public record OperatorCredentialRequest(
    @NotBlank String apiKey,
    @NotBlank String secretKey,
    String passphrase
)
{
}
