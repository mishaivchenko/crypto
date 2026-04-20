package com.crypto.funding.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "monitor.engine-plan")
public class MonitorEnginePlanProperties
{
    private int lookaheadMinutes = 120;
    private int overdueGraceSeconds = 30;
    private boolean includeClosedTrades;

    public int getLookaheadMinutes()
    {
        return lookaheadMinutes;
    }

    public void setLookaheadMinutes( int lookaheadMinutes )
    {
        this.lookaheadMinutes = Math.max( 1, lookaheadMinutes );
    }

    public int getOverdueGraceSeconds()
    {
        return overdueGraceSeconds;
    }

    public void setOverdueGraceSeconds( int overdueGraceSeconds )
    {
        this.overdueGraceSeconds = Math.max( 0, overdueGraceSeconds );
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
