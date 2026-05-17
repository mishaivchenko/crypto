package com.crypto.funding.infrastructure.persistence.model;

import com.crypto.funding.domain.venue.VenueAccessMode;
import com.crypto.funding.domain.venue.VenueConnectionStatus;
import com.crypto.funding.infrastructure.persistence.converter.InstantEpochMillisConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "venue_profile")
public class VenueProfileEntity
{
    @Id
    @Column(name = "venue", nullable = false, updatable = false)
    private String venue;

    @Enumerated(EnumType.STRING)
    @Column(name = "selected_mode", nullable = false)
    private VenueAccessMode selectedMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_status", nullable = false)
    private VenueConnectionStatus connectionStatus = VenueConnectionStatus.NOT_CONNECTED;

    @Column(name = "connection_message", columnDefinition = "TEXT")
    private String connectionMessage;

    @Column(name = "last_connection_http_status")
    private Integer lastConnectionHttpStatus;

    @Convert(converter = InstantEpochMillisConverter.class)
    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "default_manual_latency_adjustment_ms")
    private Long defaultManualLatencyAdjustmentMs;

    public String getVenue()
    {
        return venue;
    }

    public void setVenue( String venue )
    {
        this.venue = venue;
    }

    public VenueAccessMode getSelectedMode()
    {
        return selectedMode;
    }

    public void setSelectedMode( VenueAccessMode selectedMode )
    {
        this.selectedMode = selectedMode;
    }

    public VenueConnectionStatus getConnectionStatus()
    {
        return connectionStatus;
    }

    public void setConnectionStatus( VenueConnectionStatus connectionStatus )
    {
        this.connectionStatus = connectionStatus;
    }

    public String getConnectionMessage()
    {
        return connectionMessage;
    }

    public void setConnectionMessage( String connectionMessage )
    {
        this.connectionMessage = connectionMessage;
    }

    public Integer getLastConnectionHttpStatus()
    {
        return lastConnectionHttpStatus;
    }

    public void setLastConnectionHttpStatus( Integer lastConnectionHttpStatus )
    {
        this.lastConnectionHttpStatus = lastConnectionHttpStatus;
    }

    public Instant getLastCheckedAt()
    {
        return lastCheckedAt;
    }

    public void setLastCheckedAt( Instant lastCheckedAt )
    {
        this.lastCheckedAt = lastCheckedAt;
    }

    public Long getDefaultManualLatencyAdjustmentMs()
    {
        return defaultManualLatencyAdjustmentMs;
    }

    public void setDefaultManualLatencyAdjustmentMs( Long defaultManualLatencyAdjustmentMs )
    {
        this.defaultManualLatencyAdjustmentMs = defaultManualLatencyAdjustmentMs;
    }
}
