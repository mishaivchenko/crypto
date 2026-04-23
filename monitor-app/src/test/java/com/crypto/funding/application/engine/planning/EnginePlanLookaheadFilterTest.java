package com.crypto.funding.application.engine.planning;

import com.crypto.funding.config.MonitorEnginePlanProperties;
import com.crypto.funding.contract.engine.EngineExecutionPlan;
import com.crypto.funding.contract.engine.EnginePlanStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EnginePlanLookaheadFilterTest
{
    private final EnginePlanLookaheadFilter filter = new EnginePlanLookaheadFilter( properties() );

    @Test
    void alwaysKeepsActionableAndOverduePlans()
    {
        assertThat( filter.shouldInclude( plan( EnginePlanStatus.ENTRY_WINDOW, null ), false ) ).isTrue();
        assertThat( filter.shouldInclude( plan( EnginePlanStatus.EXIT_WINDOW, null ), false ) ).isTrue();
        assertThat( filter.shouldInclude( plan( EnginePlanStatus.OVERDUE, null ), false ) ).isTrue();
    }

    @Test
    void usesLookaheadWindowForWaitingPlans()
    {
        assertThat( filter.shouldInclude( plan( EnginePlanStatus.WAITING_ENTRY, 30_000L ), false ) ).isTrue();
        assertThat( filter.shouldInclude( plan( EnginePlanStatus.WAITING_ENTRY, 7_500_000L ), false ) ).isFalse();
        assertThat( filter.shouldInclude( plan( EnginePlanStatus.WAITING_ENTRY, null ), false ) ).isFalse();
    }

    @Test
    void includeAllBypassesLookaheadFilter()
    {
        assertThat( filter.shouldInclude( plan( EnginePlanStatus.CLOSED, null ), true ) ).isTrue();
    }

    private static MonitorEnginePlanProperties properties()
    {
        MonitorEnginePlanProperties properties = new MonitorEnginePlanProperties();
        properties.setLookaheadMinutes( 120 );
        return properties;
    }

    private static EngineExecutionPlan plan( EnginePlanStatus status, Long millisUntilAction )
    {
        return new EngineExecutionPlan(
            1L,
            11L,
            "bybit",
            "BTC/USDT",
            null,
            null,
            null,
            Instant.parse( "2030-01-01T00:00:00Z" ),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(),
            status,
            null,
            millisUntilAction,
            null,
            "summary"
        );
    }
}
