package com.crypto.funding.api.dto;

import com.crypto.funding.domain.autoapproval.AutoApprovalAction;
import com.crypto.funding.domain.autoapproval.AutoApprovalRule;
import com.crypto.funding.domain.ai.AiRecommendation;
import com.crypto.funding.domain.liquidity.LiquidityScore;
import com.crypto.funding.domain.trade.TradeSide;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AutoApprovalRuleResponse(
    Long id,
    String name,
    boolean enabled,
    String mode,
    BigDecimal minFundingRatePct,
    BigDecimal maxFundingRatePct,
    List<String> allowedVenues,
    List<AiRecommendation> allowedAiRecommendations,
    Double minAiConfidence,
    List<LiquidityScore> allowedLiquidityScores,
    BigDecimal defaultNotionalUsd,
    TradeSide defaultSide,
    AutoApprovalAction action,
    int priority,
    String notes,
    Instant createdAt,
    Instant updatedAt
)
{
    public static AutoApprovalRuleResponse from( AutoApprovalRule rule )
    {
        return new AutoApprovalRuleResponse(
            rule.id(),
            rule.name(),
            rule.enabled(),
            rule.mode(),
            rule.minFundingRatePct(),
            rule.maxFundingRatePct(),
            rule.allowedVenues(),
            rule.allowedAiRecommendations(),
            rule.minAiConfidence(),
            rule.allowedLiquidityScores(),
            rule.defaultNotionalUsd(),
            rule.defaultSide(),
            rule.action(),
            rule.priority(),
            rule.notes(),
            rule.createdAt(),
            rule.updatedAt()
        );
    }
}
