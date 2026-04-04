package com.crypto.funding.engine;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "engine")
public class EngineProperties
{
    private int lookaheadMinutes = 120;
    private int overdueGraceSeconds = 30;
    private boolean includeClosedTrades = false;

    public int getLookaheadMinutes()
    {
        return lookaheadMinutes;
    }

    public void setLookaheadMinutes( int lookaheadMinutes )
    {
        this.lookaheadMinutes = lookaheadMinutes;
    }

    public int getOverdueGraceSeconds()
    {
        return overdueGraceSeconds;
    }

    public void setOverdueGraceSeconds( int overdueGraceSeconds )
    {
        this.overdueGraceSeconds = overdueGraceSeconds;
    }

    public boolean isIncludeClosedTrades()
    {
        return includeClosedTrades;
    }

    public void setIncludeClosedTrades( boolean includeClosedTrades )
    {
        this.includeClosedTrades = includeClosedTrades;
    }
}
