package com.crypto.funding.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "trading.preparation")
public class TradePreparationProperties
{
    private int defaultEntryAttemptCount = 1;
    private long defaultEntrySpacingMs;
    private long defaultManualLatencyAdjustmentMs;

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
}
