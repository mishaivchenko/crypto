package com.crypto.funding.application.security;

import com.crypto.funding.config.CredentialStorageProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class CredentialStorageStartupValidator implements ApplicationRunner
{
    private final CredentialStorageProperties properties;

    public CredentialStorageStartupValidator( CredentialStorageProperties properties )
    {
        this.properties = properties;
    }

    @Override
    public void run( ApplicationArguments args )
    {
        if( properties.isEnabled()
            && properties.isRequireMasterKeyOnStartup()
            && ( properties.getMasterKeyBase64() == null || properties.getMasterKeyBase64().isBlank() ) )
        {
            throw new IllegalStateException( "Credential storage is enabled but CREDENTIALS_MASTER_KEY_BASE64 is missing." );
        }
    }
}
