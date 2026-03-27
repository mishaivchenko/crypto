package com.crypto.funding.infrastructure.persistence.model;

import com.crypto.funding.domain.trade.ArmedTradeState;
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
