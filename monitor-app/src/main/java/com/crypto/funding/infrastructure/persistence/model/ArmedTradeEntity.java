package com.crypto.funding.infrastructure.persistence.model;

import com.crypto.funding.domain.trade.ArmedTradeState;
import com.crypto.funding.domain.trade.TradeArmSource;
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
    name = "armed_trade",
    indexes = {
        @Index(name = "idx_armed_trade_event_id", columnList = "funding_event_id"),
        @Index(name = "idx_armed_trade_state", columnList = "state")
    }
)
public class ArmedTradeEntity extends AuditableEntity
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "integer")
    private Long id;

    @Column(name = "funding_event_id", nullable = false)
    private Long fundingEventId;

    @Column(name = "notional_usd", nullable = false, precision = 19, scale = 8)
    private BigDecimal notionalUsd;

    @Enumerated(EnumType.STRING)
    @Column(name = "intended_side")
    private TradeSide intendedSide;

    @Convert(converter = InstantEpochMillisConverter.class)
    @Column(name = "planned_entry_at")
    private Instant plannedEntryAt;

    @Convert(converter = InstantEpochMillisConverter.class)
    @Column(name = "planned_exit_at")
    private Instant plannedExitAt;

    @Convert(converter = InstantEpochMillisConverter.class)
    @Column(name = "armed_at", nullable = false)
    private Instant armedAt;

    @Column(name = "event_age_ms_at_arm")
    private Long eventAgeMsAtArm;

    @Column(name = "entry_lead_ms")
    private Long entryLeadMs;

    @Column(name = "exit_lead_ms")
    private Long exitLeadMs;

    @Column(name = "entry_attempt_count")
    private Integer entryAttemptCount = 1;

    @Column(name = "entry_spacing_ms")
    private Long entrySpacingMs = 0L;

    @Column(name = "measured_entry_latency_ms")
    private Long measuredEntryLatencyMs;

    @Column(name = "manual_latency_adjustment_ms")
    private Long manualLatencyAdjustmentMs;

    @Column(name = "effective_entry_latency_ms")
    private Long effectiveEntryLatencyMs;

    @Enumerated(EnumType.STRING)
    @Column(name = "arm_source")
    private TradeArmSource armSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private ArmedTradeState state = ArmedTradeState.ARMED;

    @Column(name = "notes", length = 1000)
    private String notes;

    public Long getId()
    {
        return id;
    }

    public Long getFundingEventId()
    {
        return fundingEventId;
    }

    public void setFundingEventId( Long fundingEventId )
    {
        this.fundingEventId = fundingEventId;
    }

    public BigDecimal getNotionalUsd()
    {
        return notionalUsd;
    }

    public void setNotionalUsd( BigDecimal notionalUsd )
    {
        this.notionalUsd = notionalUsd;
    }

    public TradeSide getIntendedSide()
    {
        return intendedSide;
    }

    public void setIntendedSide( TradeSide intendedSide )
    {
        this.intendedSide = intendedSide;
    }

    public Instant getPlannedEntryAt()
    {
        return plannedEntryAt;
    }

    public void setPlannedEntryAt( Instant plannedEntryAt )
    {
        this.plannedEntryAt = plannedEntryAt;
    }

    public Instant getPlannedExitAt()
    {
        return plannedExitAt;
    }

    public void setPlannedExitAt( Instant plannedExitAt )
    {
        this.plannedExitAt = plannedExitAt;
    }

    public Instant getArmedAt()
    {
        return armedAt;
    }

    public void setArmedAt( Instant armedAt )
    {
        this.armedAt = armedAt;
    }

    public Long getEventAgeMsAtArm()
    {
        return eventAgeMsAtArm;
    }

    public void setEventAgeMsAtArm( Long eventAgeMsAtArm )
    {
        this.eventAgeMsAtArm = eventAgeMsAtArm;
    }

    public Long getEntryLeadMs()
    {
        return entryLeadMs;
    }

    public void setEntryLeadMs( Long entryLeadMs )
    {
        this.entryLeadMs = entryLeadMs;
    }

    public Long getExitLeadMs()
    {
        return exitLeadMs;
    }

    public void setExitLeadMs( Long exitLeadMs )
    {
        this.exitLeadMs = exitLeadMs;
    }

    public Integer getEntryAttemptCount()
    {
        return entryAttemptCount == null ? 1 : entryAttemptCount;
    }

    public void setEntryAttemptCount( Integer entryAttemptCount )
    {
        this.entryAttemptCount = entryAttemptCount == null ? 1 : entryAttemptCount;
    }

    public Long getEntrySpacingMs()
    {
        return entrySpacingMs == null ? 0L : entrySpacingMs;
    }

    public void setEntrySpacingMs( Long entrySpacingMs )
    {
        this.entrySpacingMs = entrySpacingMs == null ? 0L : entrySpacingMs;
    }

    public Long getMeasuredEntryLatencyMs()
    {
        return measuredEntryLatencyMs;
    }

    public void setMeasuredEntryLatencyMs( Long measuredEntryLatencyMs )
    {
        this.measuredEntryLatencyMs = measuredEntryLatencyMs;
    }

    public Long getManualLatencyAdjustmentMs()
    {
        return manualLatencyAdjustmentMs;
    }

    public void setManualLatencyAdjustmentMs( Long manualLatencyAdjustmentMs )
    {
        this.manualLatencyAdjustmentMs = manualLatencyAdjustmentMs;
    }

    public Long getEffectiveEntryLatencyMs()
    {
        return effectiveEntryLatencyMs;
    }

    public void setEffectiveEntryLatencyMs( Long effectiveEntryLatencyMs )
    {
        this.effectiveEntryLatencyMs = effectiveEntryLatencyMs;
    }

    public TradeArmSource getArmSource()
    {
        return armSource;
    }

    public void setArmSource( TradeArmSource armSource )
    {
        this.armSource = armSource;
    }

    public ArmedTradeState getState()
    {
        return state;
    }

    public void setState( ArmedTradeState state )
    {
        this.state = state;
    }

    public String getNotes()
    {
        return notes;
    }

    public void setNotes( String notes )
    {
        this.notes = notes;
    }
}
