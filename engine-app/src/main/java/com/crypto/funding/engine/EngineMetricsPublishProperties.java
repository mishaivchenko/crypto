package com.crypto.funding.engine;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "engine.metrics-publish")
public class EngineMetricsPublishProperties
{
    private boolean enabled;
    private long intervalMs = 15000L;

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled( boolean enabled )
    {
        this.enabled = enabled;
    }

    public long getIntervalMs()
    {
        return intervalMs;
    }

    public void setIntervalMs( long intervalMs )
    {
        this.intervalMs = Math.max( 1000L, intervalMs );
    }
}
