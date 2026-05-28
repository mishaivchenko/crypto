package com.crypto.funding.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.cloudflare")
public class CloudflareAccessProperties
{
    private String teamDomain = "";
    private String audience = "";
    private String certsUrl = "";

    public String getTeamDomain()
    {
        return teamDomain;
    }

    public void setTeamDomain( String teamDomain )
    {
        this.teamDomain = teamDomain;
    }

    public String getAudience()
    {
        return audience;
    }

    public void setAudience( String audience )
    {
        this.audience = audience;
    }

    public String getCertsUrl()
    {
        if( certsUrl != null && !certsUrl.isBlank() )
        {
            return certsUrl;
        }
        return "https://" + teamDomain + "/cdn-cgi/access/certs";
    }

    public void setCertsUrl( String certsUrl )
    {
        this.certsUrl = certsUrl;
    }
}
