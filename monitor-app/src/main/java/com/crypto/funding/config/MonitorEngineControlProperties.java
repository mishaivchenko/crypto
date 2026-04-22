package com.crypto.funding.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "monitor.engine-control")
public class MonitorEngineControlProperties
{
    private String baseUrl = "http://localhost:8091";
    private String internalToken = "";

    public String getBaseUrl()
    {
        return baseUrl;
    }

    public void setBaseUrl( String baseUrl )
    {
        this.baseUrl = baseUrl;
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
