package com.crypto.funding.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record DevTestRunCreateRequest(
    @NotBlank String venue,
    @NotBlank String symbol,
    @NotNull @Positive @DecimalMax("25") BigDecimal notionalUsd
)
{
}
