package com.crypto.funding.domain.autoapproval;

import com.crypto.funding.domain.ai.AiRecommendation;
import com.crypto.funding.domain.liquidity.LiquidityScore;
import com.crypto.funding.domain.trade.TradeSide;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AutoApprovalRule(
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
    public AutoApprovalRule
    {
        if( name == null || name.isBlank() )
        {
            throw new IllegalArgumentException( "name must not be blank" );
        }
        if( defaultNotionalUsd == null || defaultNotionalUsd.compareTo( BigDecimal.ZERO ) <= 0 )
        {
            throw new IllegalArgumentException( "defaultNotionalUsd must be positive" );
        }
        if( defaultSide == null )
        {
            throw new IllegalArgumentException( "defaultSide must not be null" );
        }
        if( action == null )
        {
            throw new IllegalArgumentException( "action must not be null" );
        }
        allowedVenues = allowedVenues == null ? List.of() : List.copyOf( allowedVenues );
        allowedAiRecommendations = allowedAiRecommendations == null ? List.of() : List.copyOf( allowedAiRecommendations );
        allowedLiquidityScores = allowedLiquidityScores == null ? List.of() : List.copyOf( allowedLiquidityScores );
    }
}
