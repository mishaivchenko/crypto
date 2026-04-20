package com.crypto.funding.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.operators")
public class OperatorSecurityProperties
{
    private boolean authEnabled = true;
    private String bootstrapUsers = "";
    private String internalToken = "";

    public boolean isAuthEnabled()
    {
        return authEnabled;
    }

    public void setAuthEnabled( boolean authEnabled )
    {
        this.authEnabled = authEnabled;
    }

    public String getBootstrapUsers()
    {
        return bootstrapUsers;
    }

    public void setBootstrapUsers( String bootstrapUsers )
    {
        this.bootstrapUsers = bootstrapUsers;
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
