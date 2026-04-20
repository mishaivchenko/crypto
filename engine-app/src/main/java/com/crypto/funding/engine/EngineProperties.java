package com.crypto.funding.engine;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "engine")
public class EngineProperties
{
    private String monitorBaseUrl = "http://localhost:8090";
    private String internalToken = "";
    private boolean executionLoopEnabled;
    private long executionLoopIntervalMs = 1000L;

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
}
