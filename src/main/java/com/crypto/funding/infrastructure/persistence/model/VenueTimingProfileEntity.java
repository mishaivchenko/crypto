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

import java.time.Instant;

@Entity
@Table(
    name = "venue_timing_profile",
    indexes = {
        @Index(name = "idx_venue_timing_profile_venue_symbol", columnList = "venue,symbol")
    }
)
public class VenueTimingProfileEntity extends AuditableEntity
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "venue", nullable = false)
    private String venue;

    @Column(name = "symbol", nullable = false)
    private String symbol;

    @Column(name = "observed_lag_ms")
    private Long observedLagMs;

    @Column(name = "entry_latency_ms")
    private Long entryLatencyMs;

    @Column(name = "exit_latency_ms")
    private Long exitLatencyMs;

    @Convert(converter = InstantEpochMillisConverter.class)
    @Column(name = "sampled_at", nullable = false)
    private Instant sampledAt;

    @Column(name = "notes", length = 1000)
    private String notes;

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

    public Long getObservedLagMs()
    {
        return observedLagMs;
    }

    public void setObservedLagMs( Long observedLagMs )
    {
        this.observedLagMs = observedLagMs;
    }

    public Long getEntryLatencyMs()
    {
        return entryLatencyMs;
    }

    public void setEntryLatencyMs( Long entryLatencyMs )
    {
        this.entryLatencyMs = entryLatencyMs;
    }

    public Long getExitLatencyMs()
    {
        return exitLatencyMs;
    }

    public void setExitLatencyMs( Long exitLatencyMs )
    {
        this.exitLatencyMs = exitLatencyMs;
    }

    public Instant getSampledAt()
    {
        return sampledAt;
    }

    public void setSampledAt( Instant sampledAt )
    {
        this.sampledAt = sampledAt;
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
