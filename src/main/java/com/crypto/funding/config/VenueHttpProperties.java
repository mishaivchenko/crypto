package com.crypto.funding.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "trading.http")
public class VenueHttpProperties
{
    private int connectTimeoutMs = 1000;
    private int requestTimeoutMs = 5000;
    private boolean preferHttp2 = true;

    public int getConnectTimeoutMs()
    {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs( int connectTimeoutMs )
    {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getRequestTimeoutMs()
    {
        return requestTimeoutMs;
    }

    public void setRequestTimeoutMs( int requestTimeoutMs )
    {
        this.requestTimeoutMs = requestTimeoutMs;
    }

    public boolean isPreferHttp2()
    {
        return preferHttp2;
    }

    public void setPreferHttp2( boolean preferHttp2 )
    {
        this.preferHttp2 = preferHttp2;
    }
}
