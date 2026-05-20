package com.crypto.funding.infrastructure.persistence.model;

import com.crypto.funding.domain.liquidity.LiquidityScore;
import com.crypto.funding.domain.trade.TradeSide;
import com.crypto.funding.infrastructure.persistence.converter.InstantEpochMillisConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
    name = "liquidity_assessment",
    indexes = {
        @Index(name = "idx_liquidity_assessment_trade_id", columnList = "trade_id"),
        @Index(name = "idx_liquidity_assessment_venue_symbol", columnList = "venue,symbol"),
        @Index(name = "idx_liquidity_assessment_sampled_at", columnList = "sampled_at")
    }
)
public class LiquidityAssessmentEntity extends AuditableEntity
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "integer")
    private Long id;

    @Column(name = "assessment_id", nullable = false, unique = true, length = 64)
    private String assessmentId;

    @Column(name = "trade_id")
    private Long tradeId;

    @Column(name = "signal_candidate_id")
    private Long signalCandidateId;

    @Column(name = "venue", nullable = false)
    private String venue;

    @Column(name = "symbol", nullable = false)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false)
    private TradeSide side;

    @Column(name = "best_bid", precision = 19, scale = 8)
    private BigDecimal bestBid;

    @Column(name = "best_ask", precision = 19, scale = 8)
    private BigDecimal bestAsk;

    @Column(name = "spread_bps", precision = 19, scale = 4)
    private BigDecimal spreadBps;

    @Column(name = "max_slippage_bps", precision = 19, scale = 4)
    private BigDecimal maxSlippageBps;

    @Column(name = "entry_bid_depth_notional", precision = 19, scale = 8)
    private BigDecimal entryBidDepthNotional;

    @Column(name = "exit_ask_depth_notional", precision = 19, scale = 8)
    private BigDecimal exitAskDepthNotional;

    @Column(name = "round_trip_safe_notional", precision = 19, scale = 8)
    private BigDecimal roundTripSafeNotional;

    @Column(name = "safety_haircut", precision = 19, scale = 8)
    private BigDecimal safetyHaircut;

    @Column(name = "recommended_max_order_notional", precision = 19, scale = 8)
    private BigDecimal recommendedMaxOrderNotional;

    @Enumerated(EnumType.STRING)
    @Column(name = "score", nullable = false)
    private LiquidityScore score;

    @Convert(converter = InstantEpochMillisConverter.class)
    @Column(name = "sampled_at", nullable = false)
    private Instant sampledAt;

    @Convert(converter = InstantEpochMillisConverter.class)
    @Column(name = "expires_at")
    private Instant expiresAt;

    public Long getId()
    {
        return id;
    }

    public String getAssessmentId()
    {
        return assessmentId;
    }

    public void setAssessmentId( String assessmentId )
    {
        this.assessmentId = assessmentId;
    }

    public Long getTradeId()
    {
        return tradeId;
    }

    public void setTradeId( Long tradeId )
    {
        this.tradeId = tradeId;
    }

    public Long getSignalCandidateId()
    {
        return signalCandidateId;
    }

    public void setSignalCandidateId( Long signalCandidateId )
    {
        this.signalCandidateId = signalCandidateId;
    }

    public String getVenue()
    {
        return venue;
    }

    public void setVenue( String venue )
    {
        this.venue = venue;
    }

    public String getSymbol()
    {
        return symbol;
    }

    public void setSymbol( String symbol )
    {
        this.symbol = symbol;
    }

    public TradeSide getSide()
    {
        return side;
    }

    public void setSide( TradeSide side )
    {
        this.side = side;
    }

    public BigDecimal getBestBid()
    {
        return bestBid;
    }

    public void setBestBid( BigDecimal bestBid )
    {
        this.bestBid = bestBid;
    }

    public BigDecimal getBestAsk()
    {
        return bestAsk;
    }

    public void setBestAsk( BigDecimal bestAsk )
    {
        this.bestAsk = bestAsk;
    }

    public BigDecimal getSpreadBps()
    {
        return spreadBps;
    }

    public void setSpreadBps( BigDecimal spreadBps )
    {
        this.spreadBps = spreadBps;
    }

    public BigDecimal getMaxSlippageBps()
    {
        return maxSlippageBps;
    }

    public void setMaxSlippageBps( BigDecimal maxSlippageBps )
    {
        this.maxSlippageBps = maxSlippageBps;
    }

    public BigDecimal getEntryBidDepthNotional()
    {
        return entryBidDepthNotional;
    }

    public void setEntryBidDepthNotional( BigDecimal entryBidDepthNotional )
    {
        this.entryBidDepthNotional = entryBidDepthNotional;
    }

    public BigDecimal getExitAskDepthNotional()
    {
        return exitAskDepthNotional;
    }

    public void setExitAskDepthNotional( BigDecimal exitAskDepthNotional )
    {
        this.exitAskDepthNotional = exitAskDepthNotional;
    }

    public BigDecimal getRoundTripSafeNotional()
    {
        return roundTripSafeNotional;
    }

    public void setRoundTripSafeNotional( BigDecimal roundTripSafeNotional )
    {
        this.roundTripSafeNotional = roundTripSafeNotional;
    }

    public BigDecimal getSafetyHaircut()
    {
        return safetyHaircut;
    }

    public void setSafetyHaircut( BigDecimal safetyHaircut )
    {
        this.safetyHaircut = safetyHaircut;
    }

    public BigDecimal getRecommendedMaxOrderNotional()
    {
        return recommendedMaxOrderNotional;
    }

    public void setRecommendedMaxOrderNotional( BigDecimal recommendedMaxOrderNotional )
    {
        this.recommendedMaxOrderNotional = recommendedMaxOrderNotional;
    }

    public LiquidityScore getScore()
    {
        return score;
    }

    public void setScore( LiquidityScore score )
    {
        this.score = score;
    }

    public Instant getSampledAt()
    {
        return sampledAt;
    }

    public void setSampledAt( Instant sampledAt )
    {
        this.sampledAt = sampledAt;
    }

    public Instant getExpiresAt()
    {
        return expiresAt;
    }

    public void setExpiresAt( Instant expiresAt )
    {
        this.expiresAt = expiresAt;
    }
}
