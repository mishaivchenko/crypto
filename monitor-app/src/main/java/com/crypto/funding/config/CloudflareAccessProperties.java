package com.crypto.funding.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.cloudflare")
public class CloudflareAccessProperties
{
    private String teamDomain = "";
    private String audience = "";
    private String certsUrl = "";
    /**
     * Comma-separated pairs oldUsername:newEmail for one-time credential migration.
     * Each entry renames an operator_account row so credentials remain reachable
     * after the bootstrap username is replaced by a Cloudflare email.
     * Example: CLOUDFLARE_USERNAME_MIGRATIONS=alice:alice@corp.com,bob:bob@corp.com
     */
    private String usernameMigrations = "";

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

    public String getUsernameMigrations()
    {
        return usernameMigrations;
    }

    public void setUsernameMigrations( String usernameMigrations )
    {
        this.usernameMigrations = usernameMigrations;
    }
}
