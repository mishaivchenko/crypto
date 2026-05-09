package com.crypto.funding.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@ConfigurationProperties(prefix = "monitor.dev-test-tool")
public class MonitorDevTestToolProperties
{
    private boolean enabled;
    private BigDecimal maxNotionalUsd = BigDecimal.valueOf( 25 );
    private String enabledVenues = "bybit,gate";

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled( boolean enabled )
    {
        this.enabled = enabled;
    }

    public BigDecimal getMaxNotionalUsd()
    {
        return maxNotionalUsd;
    }

    public void setMaxNotionalUsd( BigDecimal maxNotionalUsd )
    {
        this.maxNotionalUsd = maxNotionalUsd;
    }

    public String getEnabledVenues()
    {
        return enabledVenues;
    }

    public void setEnabledVenues( String enabledVenues )
    {
        this.enabledVenues = enabledVenues;
    }

    public List<String> enabledVenues()
    {
        if( enabledVenues == null || enabledVenues.isBlank() )
        {
            return List.of();
        }
        return Arrays.stream( enabledVenues.split( "," ) )
                     .map( value -> value.trim().toLowerCase( Locale.ROOT ) )
                     .filter( value -> !value.isBlank() )
                     .distinct()
                     .toList();
    }
}
