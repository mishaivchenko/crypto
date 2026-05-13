package com.crypto.funding.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@ConfigurationProperties(prefix = "monitor.risk")
public class MonitorRiskProperties
{
    private int maxConcurrentArmedTrades = 3;
    private String disabledVenues = "";

    public int getMaxConcurrentArmedTrades()
    {
        return maxConcurrentArmedTrades;
    }

    public void setMaxConcurrentArmedTrades( int maxConcurrentArmedTrades )
    {
        this.maxConcurrentArmedTrades = Math.max( 1, maxConcurrentArmedTrades );
    }

    public String getDisabledVenues()
    {
        return disabledVenues;
    }

    public void setDisabledVenues( String disabledVenues )
    {
        this.disabledVenues = disabledVenues == null ? "" : disabledVenues;
    }

    public List<String> disabledVenues()
    {
        if( disabledVenues == null || disabledVenues.isBlank() )
        {
            return List.of();
        }
        return Arrays.stream( disabledVenues.split( "," ) )
                     .map( v -> v.trim().toLowerCase( Locale.ROOT ) )
                     .filter( v -> !v.isBlank() )
                     .distinct()
                     .toList();
    }
}
