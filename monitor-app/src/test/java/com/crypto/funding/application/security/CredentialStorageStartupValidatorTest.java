package com.crypto.funding.application.security;

import com.crypto.funding.config.CredentialStorageProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredentialStorageStartupValidatorTest
{
    @Test
    void failsClosedWhenCredentialStorageIsEnabledWithoutMasterKey()
    {
        CredentialStorageProperties properties = new CredentialStorageProperties();
        properties.setEnabled( true );
        properties.setRequireMasterKeyOnStartup( true );

        CredentialStorageStartupValidator validator = new CredentialStorageStartupValidator( properties );

        assertThatThrownBy( () -> validator.run( new DefaultApplicationArguments() ) )
            .isInstanceOf( IllegalStateException.class )
            .hasMessageContaining( "CREDENTIALS_MASTER_KEY_BASE64" );
    }
}
