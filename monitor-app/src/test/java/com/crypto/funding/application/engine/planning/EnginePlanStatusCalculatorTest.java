package com.crypto.funding.application.engine.planning;

import com.crypto.funding.config.MonitorEnginePlanProperties;
import com.crypto.funding.contract.engine.EnginePlanStatus;
import com.crypto.funding.domain.trade.ArmedTrade;
import com.crypto.funding.domain.trade.ArmedTradeState;
import com.crypto.funding.domain.trade.TradeArmSource;
import com.crypto.funding.domain.trade.TradeSide;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EnginePlanStatusCalculatorTest
{
    private final MonitorEnginePlanProperties properties = properties();
    private final EnginePlanStatusCalculator calculator = new EnginePlanStatusCalculator(
        properties,
        new EngineEntryAttemptScheduleBuilder()
    );

    @Test
    void returnsWaitingEntryBeforeFirstTrigger()
    {
        ArmedTrade trade = trade( ArmedTradeState.ARMED, "2030-01-01T00:00:00Z", "2030-01-01T00:05:00Z", 3, 150L, 40L );

        assertThat( calculator.deriveStatus( trade, Instant.parse( "2029-12-31T23:59:00Z" ) ) ).isEqualTo( EnginePlanStatus.WAITING_ENTRY );
    }

    @Test
    void returnsEntryWindowInsideEntryRange()
    {
        ArmedTrade trade = trade( ArmedTradeState.ENTRY_PENDING, "2030-01-01T00:00:00Z", "2030-01-01T00:05:00Z", 3, 150L, 40L );

        assertThat( calculator.deriveStatus( trade, Instant.parse( "2030-01-01T00:00:00Z" ) ) ).isEqualTo( EnginePlanStatus.ENTRY_WINDOW );
    }

    @Test
    void returnsOverdueAfterGraceWindow()
    {
        ArmedTrade trade = trade( ArmedTradeState.ENTRY_ATTEMPTED, "2030-01-01T00:00:00Z", "2030-01-01T00:05:00Z", 3, 150L, 40L );

        assertThat( calculator.deriveStatus( trade, Instant.parse( "2030-01-01T00:00:31Z" ) ) ).isEqualTo( EnginePlanStatus.OVERDUE );
    }

    @Test
    void returnsWaitingExitBeforeExitTime()
    {
        ArmedTrade trade = trade( ArmedTradeState.OPEN, "2030-01-01T00:00:00Z", "2030-01-01T00:05:00Z", 1, 0L, 0L );

        assertThat( calculator.deriveStatus( trade, Instant.parse( "2030-01-01T00:04:59Z" ) ) ).isEqualTo( EnginePlanStatus.WAITING_EXIT );
    }

    @Test
    void returnsExitWindowAtOrAfterExitTime()
    {
        ArmedTrade trade = trade( ArmedTradeState.EXIT_PENDING, "2030-01-01T00:00:00Z", "2030-01-01T00:05:00Z", 1, 0L, 0L );

        assertThat( calculator.deriveStatus( trade, Instant.parse( "2030-01-01T00:05:00Z" ) ) ).isEqualTo( EnginePlanStatus.EXIT_WINDOW );
    }

    @Test
    void returnsClosedAndInvalidStates()
    {
        ArmedTrade closed = trade( ArmedTradeState.CLOSED, "2030-01-01T00:00:00Z", "2030-01-01T00:05:00Z", 1, 0L, 0L );
        ArmedTrade failed = trade( ArmedTradeState.FAILED, "2030-01-01T00:00:00Z", "2030-01-01T00:05:00Z", 1, 0L, 0L );
        ArmedTrade missingEntry = trade( ArmedTradeState.ARMED, null, "2030-01-01T00:05:00Z", 1, 0L, 0L );

        assertThat( calculator.deriveStatus( closed, Instant.parse( "2030-01-01T00:00:00Z" ) ) ).isEqualTo( EnginePlanStatus.CLOSED );
        assertThat( calculator.deriveStatus( failed, Instant.parse( "2030-01-01T00:00:00Z" ) ) ).isEqualTo( EnginePlanStatus.INVALID );
        assertThat( calculator.deriveStatus( missingEntry, Instant.parse( "2030-01-01T00:00:00Z" ) ) ).isEqualTo( EnginePlanStatus.INVALID );
    }

    private static MonitorEnginePlanProperties properties()
    {
        MonitorEnginePlanProperties properties = new MonitorEnginePlanProperties();
        properties.setOverdueGraceSeconds( 30 );
        return properties;
    }

    private static ArmedTrade trade(
        ArmedTradeState state,
        String plannedEntryAt,
        String plannedExitAt,
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
            plannedEntryAt == null ? null : Instant.parse( plannedEntryAt ),
            plannedExitAt == null ? null : Instant.parse( plannedExitAt ),
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
