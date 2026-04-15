package com.crypto.funding.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@ConfigurationProperties(prefix = "trading.execution")
public class TradingExecutionProperties
{
    private ExecutionMode mode = ExecutionMode.DISABLED;
    private boolean legacyEnabled;
    private Set<String> liveVenues = new LinkedHashSet<>();
    private Set<String> blockedVenues = new LinkedHashSet<>(Set.of("gate"));
    private int defaultEntryAttemptCount = 1;
    private long defaultEntrySpacingMs;
    private long defaultManualLatencyAdjustmentMs;

    public ExecutionMode getMode()
    {
        return mode;
    }

    public void setMode( ExecutionMode mode )
    {
        this.mode = mode == null ? ExecutionMode.DISABLED : mode;
    }

    public boolean isLegacyEnabled()
    {
        return legacyEnabled;
    }

    public void setLegacyEnabled( boolean legacyEnabled )
    {
        this.legacyEnabled = legacyEnabled;
    }

    public Set<String> getLiveVenues()
    {
        return liveVenues;
    }

    public void setLiveVenues( Set<String> liveVenues )
    {
        this.liveVenues = normalize( liveVenues );
    }

    public Set<String> getBlockedVenues()
    {
        return blockedVenues;
    }

    public void setBlockedVenues( Set<String> blockedVenues )
    {
        this.blockedVenues = normalize( blockedVenues );
    }

    public int getDefaultEntryAttemptCount()
    {
        return defaultEntryAttemptCount;
    }

    public void setDefaultEntryAttemptCount( int defaultEntryAttemptCount )
    {
        this.defaultEntryAttemptCount = Math.max( 1, defaultEntryAttemptCount );
    }

    public long getDefaultEntrySpacingMs()
    {
        return defaultEntrySpacingMs;
    }

    public void setDefaultEntrySpacingMs( long defaultEntrySpacingMs )
    {
        this.defaultEntrySpacingMs = Math.max( 0L, defaultEntrySpacingMs );
    }

    public long getDefaultManualLatencyAdjustmentMs()
    {
        return defaultManualLatencyAdjustmentMs;
    }

    public void setDefaultManualLatencyAdjustmentMs( long defaultManualLatencyAdjustmentMs )
    {
        this.defaultManualLatencyAdjustmentMs = defaultManualLatencyAdjustmentMs;
    }

    private static Set<String> normalize( Set<String> values )
    {
        if( values == null )
        {
            return new LinkedHashSet<>();
        }
        return values.stream()
                     .filter( value -> value != null && !value.isBlank() )
                     .map( value -> value.trim().toLowerCase( Locale.ROOT ) )
                     .collect( Collectors.toCollection( LinkedHashSet::new ) );
    }
}
