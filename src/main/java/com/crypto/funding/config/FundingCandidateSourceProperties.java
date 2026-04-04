package com.crypto.funding.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "trading.candidate-source")
public class FundingCandidateSourceProperties
{
    private boolean enabled = true;
    private String url = "https://uainvest.com.ua/api/funding?sort_by=funding&sort_dir=asc&limit=30";
    private int refreshIntervalSeconds = 60;
    private String sourceType = "FUNDING_API";

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled( boolean enabled )
    {
        this.enabled = enabled;
    }

    public String getUrl()
    {
        return url;
    }

    public void setUrl( String url )
    {
        this.url = url;
    }

    public int getRefreshIntervalSeconds()
    {
        return refreshIntervalSeconds;
    }

    public void setRefreshIntervalSeconds( int refreshIntervalSeconds )
    {
        this.refreshIntervalSeconds = refreshIntervalSeconds;
    }

    public String getSourceType()
    {
        return sourceType;
    }

    public void setSourceType( String sourceType )
    {
        this.sourceType = sourceType;
    }
}
