package com.crypto.funding.api.dto;

import com.crypto.funding.domain.liquidity.LiquidityScore;
import com.crypto.funding.domain.trade.TradeSide;

import java.math.BigDecimal;
import java.time.Instant;

public record LiquidityAssessmentResponse(
    String id,
    Long tradeId,
    String venue,
    String symbol,
    TradeSide side,
    BigDecimal bestBid,
    BigDecimal bestAsk,
    BigDecimal spreadBps,
    BigDecimal maxSlippageBps,
    BigDecimal entryBidDepthNotional,
    BigDecimal exitAskDepthNotional,
    BigDecimal roundTripSafeNotional,
    BigDecimal safetyHaircut,
    BigDecimal recommendedMaxOrderNotional,
    LiquidityScore score,
    boolean warning,
    Instant sampledAt,
    Instant expiresAt
)
{
}
