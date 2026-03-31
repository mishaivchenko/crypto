package com.crypto.funding.infrastructure.persistence.model;

import com.crypto.funding.infrastructure.persistence.converter.InstantEpochMillisConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
    name = "trade_outcome",
    indexes = {
        @Index(name = "idx_trade_outcome_trade_id", columnList = "armed_trade_id")
    }
)
public class TradeOutcomeEntity extends AuditableEntity
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "armed_trade_id", nullable = false)
    private Long armedTradeId;

    @Column(name = "gross_pnl_usd", precision = 19, scale = 8)
    private BigDecimal grossPnlUsd;

    @Column(name = "net_pnl_usd", precision = 19, scale = 8)
    private BigDecimal netPnlUsd;

    @Column(name = "fees_usd", precision = 19, scale = 8)
    private BigDecimal feesUsd;

    @Column(name = "outcome_code", nullable = false)
    private String outcomeCode;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Convert(converter = InstantEpochMillisConverter.class)
    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;

    public Long getId()
    {
        return id;
    }

    public Long getArmedTradeId()
    {
        return armedTradeId;
    }

    public void setArmedTradeId( Long armedTradeId )
    {
        this.armedTradeId = armedTradeId;
    }

    public BigDecimal getGrossPnlUsd()
    {
        return grossPnlUsd;
    }

    public void setGrossPnlUsd( BigDecimal grossPnlUsd )
    {
        this.grossPnlUsd = grossPnlUsd;
    }

    public BigDecimal getNetPnlUsd()
    {
        return netPnlUsd;
    }

    public void setNetPnlUsd( BigDecimal netPnlUsd )
    {
        this.netPnlUsd = netPnlUsd;
    }

    public BigDecimal getFeesUsd()
    {
        return feesUsd;
    }

    public void setFeesUsd( BigDecimal feesUsd )
    {
        this.feesUsd = feesUsd;
    }

    public String getOutcomeCode()
    {
        return outcomeCode;
    }

    public void setOutcomeCode( String outcomeCode )
    {
        this.outcomeCode = outcomeCode;
    }

    public String getNotes()
    {
        return notes;
    }

    public void setNotes( String notes )
    {
        this.notes = notes;
    }

    public Instant getEvaluatedAt()
    {
        return evaluatedAt;
    }

    public void setEvaluatedAt( Instant evaluatedAt )
    {
        this.evaluatedAt = evaluatedAt;
    }
}
