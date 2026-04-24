package com.crypto.funding.engine;

import com.crypto.funding.domain.execution.ExecutionType;
import com.crypto.funding.domain.execution.OrderAttemptStatus;
import com.crypto.funding.domain.execution.OrderIntent;
import com.crypto.funding.domain.trade.TradeSide;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialAwareExecutionPortTest
{
    // REQ: ENG-CRED-001
    // REQ: ENG-CRED-003
    @Test
    void failsWithNormalizedVenueWhenApiKeyOrSecretIsMissing()
    {
        CredentialAwareExecutionPort port = new CredentialAwareExecutionPort( new MockEnvironment() );

        var attempt = port.submitOrder( 5L, " ByBit ", "REQ/USDT", marketIntent() );

        assertThat( attempt.status() ).isEqualTo( OrderAttemptStatus.FAILED );
        assertThat( attempt.venue() ).isEqualTo( "bybit" );
        assertThat( attempt.failureReason() ).contains( "Missing engine credentials for bybit." );
        assertThat( attempt.failureReason() ).contains( "engine.credentials.bybit.api-key" );
        assertThat( attempt.failureReason() ).contains( "engine.credentials.bybit.secret-key" );
    }

    // REQ: ENG-CRED-002
    @ParameterizedTest
    @ValueSource(strings = { "bitget", "okx", "kucoin" })
    void requiresPassphraseOnlyForSpecificVenues( String venue )
    {
        MockEnvironment environment = new MockEnvironment()
            .withProperty( "engine.credentials." + venue + ".api-key", "key" )
            .withProperty( "engine.credentials." + venue + ".secret-key", "secret" );
        CredentialAwareExecutionPort port = new CredentialAwareExecutionPort( environment );

        var attempt = port.submitOrder( 5L, venue, "REQ/USDT", marketIntent() );

        assertThat( attempt.status() ).isEqualTo( OrderAttemptStatus.FAILED );
        assertThat( attempt.failureReason() ).contains( "Missing engine passphrase for " + venue + "." );
        assertThat( attempt.failureReason() ).contains( "engine.credentials." + venue + ".passphrase" );
    }

    // REQ: ENG-CRED-002
    // REQ: ENG-CRED-004
    @Test
    void remainsGuardedWhenNonPassphraseVenueHasCredentials()
    {
        MockEnvironment environment = new MockEnvironment()
            .withProperty( "engine.credentials.bybit.api-key", "key" )
            .withProperty( "engine.credentials.bybit.secret-key", "secret" );
        CredentialAwareExecutionPort port = new CredentialAwareExecutionPort( environment );

        var attempt = port.submitOrder( 5L, "bybit", "REQ/USDT", marketIntent() );

        assertThat( attempt.status() ).isEqualTo( OrderAttemptStatus.FAILED );
        assertThat( attempt.failureReason() ).contains( "live order adapters are still guarded" );
    }

    // REQ: ENG-CRED-002
    // REQ: ENG-CRED-004
    @Test
    void remainsGuardedWhenPassphraseVenueHasAllCredentials()
    {
        MockEnvironment environment = new MockEnvironment()
            .withProperty( "engine.credentials.okx.api-key", "key" )
            .withProperty( "engine.credentials.okx.secret-key", "secret" )
            .withProperty( "engine.credentials.okx.passphrase", "passphrase" );
        CredentialAwareExecutionPort port = new CredentialAwareExecutionPort( environment );

        var attempt = port.submitOrder( 5L, " OKX ", "REQ/USDT", marketIntent() );

        assertThat( attempt.status() ).isEqualTo( OrderAttemptStatus.FAILED );
        assertThat( attempt.venue() ).isEqualTo( "okx" );
        assertThat( attempt.failureReason() ).contains( "live order adapters are still guarded" );
    }

    // REQ: ENG-CRED-001
    @Test
    void treatsBlankCredentialsAsMissing()
    {
        MockEnvironment environment = new MockEnvironment()
            .withProperty( "engine.credentials.bybit.api-key", " " )
            .withProperty( "engine.credentials.bybit.secret-key", "secret" );
        CredentialAwareExecutionPort port = new CredentialAwareExecutionPort( environment );

        var attempt = port.submitOrder( 5L, "bybit", "REQ/USDT", marketIntent() );

        assertThat( attempt.status() ).isEqualTo( OrderAttemptStatus.FAILED );
        assertThat( attempt.failureReason() ).contains( "Missing engine credentials for bybit." );
    }

    // REQ: ENG-CRED-002
    @Test
    void treatsBlankPassphraseAsMissingForPassphraseVenue()
    {
        MockEnvironment environment = new MockEnvironment()
            .withProperty( "engine.credentials.okx.api-key", "key" )
            .withProperty( "engine.credentials.okx.secret-key", "secret" )
            .withProperty( "engine.credentials.okx.passphrase", " " );
        CredentialAwareExecutionPort port = new CredentialAwareExecutionPort( environment );

        var attempt = port.submitOrder( 5L, "okx", "REQ/USDT", marketIntent() );

        assertThat( attempt.status() ).isEqualTo( OrderAttemptStatus.FAILED );
        assertThat( attempt.failureReason() ).contains( "Missing engine passphrase for okx." );
    }

    // REQ: ENG-CRED-001
    @Test
    void treatsBlankSecretAsMissing()
    {
        MockEnvironment environment = new MockEnvironment()
            .withProperty( "engine.credentials.bybit.api-key", "key" )
            .withProperty( "engine.credentials.bybit.secret-key", " " );
        CredentialAwareExecutionPort port = new CredentialAwareExecutionPort( environment );

        var attempt = port.submitOrder( 5L, "bybit", "REQ/USDT", marketIntent() );

        assertThat( attempt.status() ).isEqualTo( OrderAttemptStatus.FAILED );
        assertThat( attempt.failureReason() ).contains( "Missing engine credentials for bybit." );
    }

    private static OrderIntent marketIntent()
    {
        return new OrderIntent( TradeSide.SHORT, ExecutionType.MARKET, BigDecimal.valueOf( 25 ), null, null );
    }
}
