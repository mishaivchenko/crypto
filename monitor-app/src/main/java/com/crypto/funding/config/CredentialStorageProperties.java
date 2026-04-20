package com.crypto.funding.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "credentials.storage")
public class CredentialStorageProperties
{
    private boolean enabled;
    private boolean requireMasterKeyOnStartup = true;
    private String masterKeyBase64 = "";

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled( boolean enabled )
    {
        this.enabled = enabled;
    }

    public boolean isRequireMasterKeyOnStartup()
    {
        return requireMasterKeyOnStartup;
    }

    public void setRequireMasterKeyOnStartup( boolean requireMasterKeyOnStartup )
    {
        this.requireMasterKeyOnStartup = requireMasterKeyOnStartup;
    }

    public String getMasterKeyBase64()
    {
        return masterKeyBase64;
    }

    public void setMasterKeyBase64( String masterKeyBase64 )
    {
        this.masterKeyBase64 = masterKeyBase64;
    }
}
