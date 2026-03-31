package com.crypto.funding.application.venue;

import com.crypto.funding.config.MetadataSyncProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VenueCredentialStartupValidatorTest
{
    @Test
    void allowsStartupWhenCredentialCheckDisabled()
    {
        MetadataSyncProperties properties = new MetadataSyncProperties();
        properties.setRequireCredentialsOnStartup( false );
        properties.setEnabledVenues( List.of( "binance" ) );

        VenueCredentialStartupValidator validator = new VenueCredentialStartupValidator( properties, new MockEnvironment() );

        assertThatCode( () -> validator.run( new DefaultApplicationArguments( new String[0] ) ) ).doesNotThrowAnyException();
    }

    @Test
    void failsWhenEnabledVenueCredentialsAreMissingForConfiguredMode()
    {
        MetadataSyncProperties properties = new MetadataSyncProperties();
        properties.setRequireCredentialsOnStartup( true );
        properties.setEnabledVenues( List.of( "binance", "gate" ) );

        MockEnvironment environment = new MockEnvironment()
            .withProperty( "trading.binance.mode", "production" )
            .withProperty( "trading.binance.production.api-key", "key" )
            .withProperty( "trading.binance.production.secret-key", "secret" )
            .withProperty( "trading.gate.mode", "testnet" );

        VenueCredentialStartupValidator validator = new VenueCredentialStartupValidator( properties, environment );

        assertThatThrownBy( () -> validator.run( new DefaultApplicationArguments( new String[0] ) ) )
            .isInstanceOf( IllegalStateException.class )
            .hasMessageContaining( "gate" )
            .hasMessageNotContaining( "binance" );
    }
}
