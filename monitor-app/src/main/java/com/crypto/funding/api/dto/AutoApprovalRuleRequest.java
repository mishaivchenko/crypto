package com.crypto.funding.api.dto;

import com.crypto.funding.domain.autoapproval.AutoApprovalAction;
import com.crypto.funding.domain.trade.TradeSide;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

public record AutoApprovalRuleRequest(
    @NotBlank String name,
    boolean enabled,
    String mode,
    BigDecimal minFundingRatePct,
    BigDecimal maxFundingRatePct,
    List<String> allowedVenues,
    List<String> allowedAiRecommendations,
    @DecimalMin("0.0") BigDecimal minAiConfidence,
    List<String> allowedLiquidityScores,
    @NotNull @Positive BigDecimal defaultNotionalUsd,
    @NotNull TradeSide defaultSide,
    @NotNull AutoApprovalAction action,
    int priority,
    String notes
)
{
}
