package com.crypto.funding.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "trading.metadata")
public class MetadataSyncProperties
{
    private boolean syncOnStartup = true;
    private boolean scheduleEnabled = false;
    private int syncIntervalMinutes = 240;
    private boolean requireCredentialsOnStartup = true;
    private boolean bootstrapFallbackEnabled = false;
    private List<String> enabledVenues = List.of( "bybit", "gate", "bitget", "okx", "kucoin" );

    public boolean isSyncOnStartup()
    {
        return syncOnStartup;
    }

    public void setSyncOnStartup( boolean syncOnStartup )
    {
        this.syncOnStartup = syncOnStartup;
    }

    public boolean isScheduleEnabled()
    {
        return scheduleEnabled;
    }

    public void setScheduleEnabled( boolean scheduleEnabled )
    {
        this.scheduleEnabled = scheduleEnabled;
    }

    public int getSyncIntervalMinutes()
    {
        return syncIntervalMinutes;
    }

    public void setSyncIntervalMinutes( int syncIntervalMinutes )
    {
        this.syncIntervalMinutes = syncIntervalMinutes;
    }

    public boolean isRequireCredentialsOnStartup()
    {
        return requireCredentialsOnStartup;
    }

    public void setRequireCredentialsOnStartup( boolean requireCredentialsOnStartup )
    {
        this.requireCredentialsOnStartup = requireCredentialsOnStartup;
    }

    public boolean isBootstrapFallbackEnabled()
    {
        return bootstrapFallbackEnabled;
    }

    public void setBootstrapFallbackEnabled( boolean bootstrapFallbackEnabled )
    {
        this.bootstrapFallbackEnabled = bootstrapFallbackEnabled;
    }

    public List<String> getEnabledVenues()
    {
        return enabledVenues;
    }

    public void setEnabledVenues( List<String> enabledVenues )
    {
        this.enabledVenues = enabledVenues;
    }
}
