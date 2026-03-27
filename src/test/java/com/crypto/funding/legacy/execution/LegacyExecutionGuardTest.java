package com.crypto.funding.legacy.execution;

import com.crypto.funding.config.ExecutionMode;
import com.crypto.funding.config.TradingExecutionProperties;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegacyExecutionGuardTest
{
    @Test
    void defaultPropertiesFailClosed()
    {
        TradingExecutionProperties properties = new TradingExecutionProperties();
        LegacyExecutionGuard guard = new LegacyExecutionGuard( properties );

        LegacyExecutionDecision decision = guard.evaluate( Set.of( "bybit" ), "unit-test" );

        assertThat( decision.allowed() ).isFalse();
        assertThat( decision.mode() ).isEqualTo( ExecutionMode.DISABLED );
        assertThat( decision.reason() ).contains( "mode=DISABLED" );
    }

    @Test
    void blockedVenueWinsEvenInLiveMode()
    {
        TradingExecutionProperties properties = new TradingExecutionProperties();
        properties.setMode( ExecutionMode.LIVE );
        properties.setLegacyEnabled( true );
        properties.setLiveVenues( Set.of( "gate", "bybit" ) );
        properties.setBlockedVenues( Set.of( "gate" ) );
        LegacyExecutionGuard guard = new LegacyExecutionGuard( properties );

        LegacyExecutionDecision bybitDecision = guard.evaluate( Set.of( "bybit" ), "unit-test" );
        LegacyExecutionDecision gateDecision = guard.evaluate( Set.of( "gate" ), "unit-test" );

        assertThat( bybitDecision.allowed() ).isTrue();
        assertThat( bybitDecision.executableVenues() ).containsExactly( "bybit" );
        assertThat( gateDecision.allowed() ).isFalse();
        assertThat( gateDecision.executableVenues() ).isEmpty();
        assertThatThrownBy( () -> guard.requireVenue( "gate", "unit-test" ) )
            .isInstanceOf( LegacyExecutionBlockedException.class );
    }
}
