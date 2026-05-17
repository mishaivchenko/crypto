package com.crypto.funding.application.engine.planning;

import com.crypto.funding.contract.engine.EngineEntryAttemptPlan;
import com.crypto.funding.domain.trade.ArmedTrade;
import com.crypto.funding.domain.trade.ArmedTradeState;
import com.crypto.funding.domain.trade.TradeArmSource;
import com.crypto.funding.domain.trade.TradeSide;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EngineEntryAttemptScheduleBuilderTest
{
    private final EngineEntryAttemptScheduleBuilder builder = new EngineEntryAttemptScheduleBuilder();

    @Test
    void buildsBurstAttemptScheduleWithLatencyOffsets()
    {
        ArmedTrade trade = trade(
            ArmedTradeState.ARMED,
            Instant.parse( "2030-01-01T00:00:00Z" ),
            Instant.parse( "2030-01-01T00:02:00Z" ),
            3,
            150L,
            40L
        );

        List<EngineEntryAttemptPlan> attempts = builder.build( trade, Instant.parse( "2029-12-31T23:59:00Z" ) );

        assertThat( attempts ).hasSize( 3 );
        assertThat( attempts.get( 0 ).targetEntryAt() ).isEqualTo( Instant.parse( "2030-01-01T00:00:00Z" ) );
        assertThat( attempts.get( 0 ).triggerAt() ).isEqualTo( Instant.parse( "2029-12-31T23:59:59.960Z" ) );
        assertThat( attempts.get( 1 ).offsetFromFirstEntryMs() ).isEqualTo( 150L );
        assertThat( attempts.get( 2 ).targetEntryAt() ).isEqualTo( Instant.parse( "2030-01-01T00:00:00.300Z" ) );
    }

    @Test
    void returnsEmptyPlanWhenEntryTimeIsMissing()
    {
        ArmedTrade trade = trade( ArmedTradeState.ARMED, null, Instant.parse( "2030-01-01T00:02:00Z" ), 2, 150L, 40L );

        assertThat( builder.build( trade, Instant.parse( "2029-12-31T23:59:00Z" ) ) ).isEmpty();
    }

    @Test
    void derivesFirstTriggerAndLastTargetUsingDefaults()
    {
        ArmedTrade trade = trade(
            ArmedTradeState.ARMED,
            Instant.parse( "2030-01-01T00:00:00Z" ),
            Instant.parse( "2030-01-01T00:02:00Z" ),
            null,
            null,
            null
        );

        assertThat( builder.firstTriggerAt( trade ) ).isEqualTo( Instant.parse( "2030-01-01T00:00:00Z" ) );
        assertThat( builder.lastTargetAt( trade ) ).isEqualTo( Instant.parse( "2030-01-01T00:00:00Z" ) );
    }

    private static ArmedTrade trade(
        ArmedTradeState state,
        Instant plannedEntryAt,
        Instant plannedExitAt,
        Integer attempts,
        Long spacingMs,
        Long effectiveLatencyMs
    )
    {
        return new ArmedTrade(
            1L,
            11L,
            BigDecimal.valueOf( 25 ),
            TradeSide.SHORT,
            plannedEntryAt,
            plannedExitAt,
            Instant.parse( "2029-12-31T23:30:00Z" ),
            null,
            null,
            null,
            attempts,
            spacingMs,
            25L,
            0L,
            effectiveLatencyMs,
            TradeArmSource.EVENT_API,
            state,
            null,
            null,
            null,
            null,
            Instant.parse( "2029-12-31T23:00:00Z" ),
            Instant.parse( "2029-12-31T23:00:00Z" )
        );
    }
}
