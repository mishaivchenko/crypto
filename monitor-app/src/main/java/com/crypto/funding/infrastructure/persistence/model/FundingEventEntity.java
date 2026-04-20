package com.crypto.funding.infrastructure.persistence.model;

import com.crypto.funding.domain.event.FundingEventStatus;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
    name = "funding_event",
    indexes = {
        @Index(name = "idx_funding_event_venue_symbol", columnList = "venue,symbol"),
        @Index(name = "idx_funding_event_funding_time", columnList = "funding_time")
    }
)
public class FundingEventEntity extends AuditableEntity
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "venue", nullable = false)
    private String venue;

    @Column(name = "symbol", nullable = false)
    private String symbol;

    @Convert(converter = InstantEpochMillisConverter.class)
    @Column(name = "funding_time", nullable = false)
    private Instant fundingTime;

    @Column(name = "funding_rate_pct", precision = 19, scale = 8)
    private BigDecimal fundingRatePct;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private FundingEventStatus status = FundingEventStatus.DISCOVERED;

    @Column(name = "source_type")
    private String sourceType;

    @Column(name = "source_ref")
    private String sourceRef;

    @Column(name = "signal_candidate_id")
    private Long signalCandidateId;

    @Convert(converter = InstantEpochMillisConverter.class)
    @Column(name = "discovered_at", nullable = false)
    private Instant discoveredAt;

    @PrePersist
    void beforePersist()
    {
        if( discoveredAt == null )
        {
            discoveredAt = Instant.now();
        }
    }

    public Long getId()
    {
        return id;
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

    public Instant getFundingTime()
    {
        return fundingTime;
    }

    public void setFundingTime( Instant fundingTime )
    {
        this.fundingTime = fundingTime;
    }

    public BigDecimal getFundingRatePct()
    {
        return fundingRatePct;
    }

    public void setFundingRatePct( BigDecimal fundingRatePct )
    {
        this.fundingRatePct = fundingRatePct;
    }

    public FundingEventStatus getStatus()
    {
        return status;
    }

    public void setStatus( FundingEventStatus status )
    {
        this.status = status;
    }

    public String getSourceType()
    {
        return sourceType;
    }

    public void setSourceType( String sourceType )
    {
        this.sourceType = sourceType;
    }

    public String getSourceRef()
    {
        return sourceRef;
    }

    public void setSourceRef( String sourceRef )
    {
        this.sourceRef = sourceRef;
    }

    public Long getSignalCandidateId()
    {
        return signalCandidateId;
    }

    public void setSignalCandidateId( Long signalCandidateId )
    {
        this.signalCandidateId = signalCandidateId;
    }

    public Instant getDiscoveredAt()
    {
        return discoveredAt;
    }

    public void setDiscoveredAt( Instant discoveredAt )
    {
        this.discoveredAt = discoveredAt;
    }
}
