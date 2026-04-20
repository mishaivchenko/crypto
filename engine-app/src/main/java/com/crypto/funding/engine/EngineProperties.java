package com.crypto.funding.engine;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "engine")
public class EngineProperties
{
    private String monitorBaseUrl = "http://localhost:8090";
    private String internalToken = "";

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
}
