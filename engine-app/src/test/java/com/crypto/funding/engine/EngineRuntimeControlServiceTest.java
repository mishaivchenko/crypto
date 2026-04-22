package com.crypto.funding.engine;

import com.crypto.funding.contract.engine.EngineRuntimeControlRequest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class EngineRuntimeControlServiceTest
{
    @Test
    void updatesLoopStateAndEnforcesMinimumInterval()
    {
        EngineProperties properties = new EngineProperties();
        properties.setExecutionLoopEnabled( true );
        properties.setExecutionLoopIntervalMs( 1000 );
        EngineTelemetryService telemetryService = new EngineTelemetryService();
        EngineRuntimeControlService service = new EngineRuntimeControlService(
            properties,
            telemetryService,
            Clock.fixed( Instant.parse( "2030-01-01T00:00:00Z" ), ZoneOffset.UTC )
        );

        var response = service.update( new EngineRuntimeControlRequest( false, 10L ) );

        assertThat( response.executionLoopEnabled() ).isFalse();
        assertThat( response.executionLoopIntervalMs() ).isEqualTo( EngineRuntimeControlService.MIN_EXECUTION_LOOP_INTERVAL_MS );
        assertThat( response.minimumExecutionLoopIntervalMs() ).isEqualTo( EngineRuntimeControlService.MIN_EXECUTION_LOOP_INTERVAL_MS );
    }
}
