package com.crypto.funding.infrastructure.persistence.model;

import com.crypto.funding.domain.autoapproval.AutoApprovalAction;
import com.crypto.funding.domain.trade.TradeSide;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(
    name = "auto_approval_rule",
    indexes = {
        @Index(name = "idx_auto_approval_rule_enabled_priority", columnList = "enabled, priority")
    }
)
public class AutoApprovalRuleEntity extends AuditableEntity
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "integer")
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "mode", nullable = false, length = 20)
    private String mode = "BOTH";

    @Column(name = "min_funding_rate_pct", precision = 10, scale = 6)
    private BigDecimal minFundingRatePct;

    @Column(name = "max_funding_rate_pct", precision = 10, scale = 6)
    private BigDecimal maxFundingRatePct;

    @Column(name = "allowed_venues", columnDefinition = "TEXT")
    private String allowedVenues;

    @Column(name = "allowed_ai_recommendations", columnDefinition = "TEXT")
    private String allowedAiRecommendations;

    @Column(name = "min_ai_confidence", precision = 4, scale = 3)
    private BigDecimal minAiConfidence;

    @Column(name = "allowed_liquidity_scores", columnDefinition = "TEXT")
    private String allowedLiquidityScores;

    @Column(name = "default_notional_usd", nullable = false, precision = 18, scale = 2)
    private BigDecimal defaultNotionalUsd = new BigDecimal( "100.00" );

    @Enumerated(EnumType.STRING)
    @Column(name = "default_side", nullable = false, length = 10)
    private TradeSide defaultSide = TradeSide.SHORT;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private AutoApprovalAction action = AutoApprovalAction.AUTO_EXECUTE;

    @Column(name = "priority", nullable = false)
    private int priority = 100;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    public Long getId() { return id; }

    public String getName() { return name; }
    public void setName( String name ) { this.name = name; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled( boolean enabled ) { this.enabled = enabled; }

    public String getMode() { return mode; }
    public void setMode( String mode ) { this.mode = mode; }

    public BigDecimal getMinFundingRatePct() { return minFundingRatePct; }
    public void setMinFundingRatePct( BigDecimal minFundingRatePct ) { this.minFundingRatePct = minFundingRatePct; }

    public BigDecimal getMaxFundingRatePct() { return maxFundingRatePct; }
    public void setMaxFundingRatePct( BigDecimal maxFundingRatePct ) { this.maxFundingRatePct = maxFundingRatePct; }

    public String getAllowedVenues() { return allowedVenues; }
    public void setAllowedVenues( String allowedVenues ) { this.allowedVenues = allowedVenues; }

    public String getAllowedAiRecommendations() { return allowedAiRecommendations; }
    public void setAllowedAiRecommendations( String allowedAiRecommendations ) { this.allowedAiRecommendations = allowedAiRecommendations; }

    public BigDecimal getMinAiConfidence() { return minAiConfidence; }
    public void setMinAiConfidence( BigDecimal minAiConfidence ) { this.minAiConfidence = minAiConfidence; }

    public String getAllowedLiquidityScores() { return allowedLiquidityScores; }
    public void setAllowedLiquidityScores( String allowedLiquidityScores ) { this.allowedLiquidityScores = allowedLiquidityScores; }

    public BigDecimal getDefaultNotionalUsd() { return defaultNotionalUsd; }
    public void setDefaultNotionalUsd( BigDecimal defaultNotionalUsd ) { this.defaultNotionalUsd = defaultNotionalUsd; }

    public TradeSide getDefaultSide() { return defaultSide; }
    public void setDefaultSide( TradeSide defaultSide ) { this.defaultSide = defaultSide; }

    public AutoApprovalAction getAction() { return action; }
    public void setAction( AutoApprovalAction action ) { this.action = action; }

    public int getPriority() { return priority; }
    public void setPriority( int priority ) { this.priority = priority; }

    public String getNotes() { return notes; }
    public void setNotes( String notes ) { this.notes = notes; }
}
