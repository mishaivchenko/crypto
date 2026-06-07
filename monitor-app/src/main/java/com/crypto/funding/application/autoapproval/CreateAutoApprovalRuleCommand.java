package com.crypto.funding.application.autoapproval;

import com.crypto.funding.domain.autoapproval.AutoApprovalAction;
import com.crypto.funding.domain.trade.TradeSide;

import java.math.BigDecimal;
import java.util.List;

public record CreateAutoApprovalRuleCommand(
    String name,
    boolean enabled,
    String mode,
    BigDecimal minFundingRatePct,
    BigDecimal maxFundingRatePct,
    List<String> allowedVenues,
    List<String> allowedAiRecommendations,
    BigDecimal minAiConfidence,
    List<String> allowedLiquidityScores,
    BigDecimal defaultNotionalUsd,
    TradeSide defaultSide,
    AutoApprovalAction action,
    int priority,
    String notes
)
{
}
