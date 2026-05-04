package com.crypto.funding.engine;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@ConfigurationProperties(prefix = "engine")
public class EngineProperties
{
    private String monitorBaseUrl = "http://localhost:8090";
    private String internalToken = "";
    private boolean executionLoopEnabled;
    private long executionLoopIntervalMs = 1000L;
    private boolean liveOrderEnabled;
    private boolean killSwitchEnabled = true;
    private String liveEnabledVenues = "bybit,gate";
    private BigDecimal maxNotionalUsd = BigDecimal.valueOf( 25 );
    private long metadataMaxAgeMinutes = 240L;
    private long latencyMaxAgeMinutes = 1440L;
    private String tradingVenueAccessMode = "testnet";

    public String getMonitorBaseUrl()
    {
        return monitorBaseUrl;
    }

    public void setMonitorBaseUrl( String monitorBaseUrl )
    {
        this.monitorBaseUrl = monitorBaseUrl;
    }

    public String getInternalToken()
    {
        return internalToken;
    }

    public void setInternalToken( String internalToken )
    {
        this.internalToken = internalToken;
    }

    public boolean isExecutionLoopEnabled()
    {
        return executionLoopEnabled;
    }

    public void setExecutionLoopEnabled( boolean executionLoopEnabled )
    {
        this.executionLoopEnabled = executionLoopEnabled;
    }

    public long getExecutionLoopIntervalMs()
    {
        return executionLoopIntervalMs;
    }

    public void setExecutionLoopIntervalMs( long executionLoopIntervalMs )
    {
        this.executionLoopIntervalMs = Math.max( 100L, executionLoopIntervalMs );
    }

    public boolean isLiveOrderEnabled()
    {
        return liveOrderEnabled;
    }

    public void setLiveOrderEnabled( boolean liveOrderEnabled )
    {
        this.liveOrderEnabled = liveOrderEnabled;
    }

    public boolean isKillSwitchEnabled()
    {
        return killSwitchEnabled;
    }

    public void setKillSwitchEnabled( boolean killSwitchEnabled )
    {
        this.killSwitchEnabled = killSwitchEnabled;
    }

    public String getLiveEnabledVenues()
    {
        return liveEnabledVenues;
    }

    public void setLiveEnabledVenues( String liveEnabledVenues )
    {
        this.liveEnabledVenues = liveEnabledVenues == null ? "" : liveEnabledVenues;
    }

    public List<String> liveEnabledVenues()
    {
        return Arrays.stream( liveEnabledVenues.split( "," ) )
                     .map( venue -> venue.trim().toLowerCase( Locale.ROOT ) )
                     .filter( venue -> !venue.isBlank() )
                     .distinct()
                     .toList();
    }

    public BigDecimal getMaxNotionalUsd()
    {
        return maxNotionalUsd;
    }

    public void setMaxNotionalUsd( BigDecimal maxNotionalUsd )
    {
        this.maxNotionalUsd = maxNotionalUsd == null ? BigDecimal.valueOf( 25 ) : maxNotionalUsd;
    }

    public long getMetadataMaxAgeMinutes()
    {
        return metadataMaxAgeMinutes;
    }

    public void setMetadataMaxAgeMinutes( long metadataMaxAgeMinutes )
    {
        this.metadataMaxAgeMinutes = Math.max( 1L, metadataMaxAgeMinutes );
    }

    public long getLatencyMaxAgeMinutes()
    {
        return latencyMaxAgeMinutes;
    }

    public void setLatencyMaxAgeMinutes( long latencyMaxAgeMinutes )
    {
        this.latencyMaxAgeMinutes = Math.max( 1L, latencyMaxAgeMinutes );
    }

    public String getTradingVenueAccessMode()
    {
        return tradingVenueAccessMode;
    }

    public void setTradingVenueAccessMode( String tradingVenueAccessMode )
    {
        this.tradingVenueAccessMode = tradingVenueAccessMode == null || tradingVenueAccessMode.isBlank()
                                      ? "testnet"
                                      : tradingVenueAccessMode.trim().toLowerCase( Locale.ROOT );
    }
}
