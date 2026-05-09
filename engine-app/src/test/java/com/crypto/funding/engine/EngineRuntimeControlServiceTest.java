package com.crypto.funding.engine;

import com.crypto.funding.contract.engine.EngineRuntimeControlRequest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class EngineRuntimeControlServiceTest
{
    @Test
    void updatesLoopStateAndEnforcesMinimumInterval()
    {
        // REQ: ENG-RTC-001
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

    @Test
    void respectsDispatchWindowAndResetsAfterRuntimeUpdate()
    {
        // REQ: ENG-RTC-002
        EngineProperties properties = new EngineProperties();
        properties.setExecutionLoopEnabled( true );
        properties.setExecutionLoopIntervalMs( 1000 );
        EngineTelemetryService telemetryService = new EngineTelemetryService();
        MutableClock clock = new MutableClock( Instant.parse( "2030-01-01T00:00:00Z" ) );
        EngineRuntimeControlService service = new EngineRuntimeControlService( properties, telemetryService, clock );

        assertThat( service.shouldRunScheduledLoop() ).isTrue();
        assertThat( service.shouldRunScheduledLoop() ).isFalse();

        clock.set( Instant.parse( "2030-01-01T00:00:00.999Z" ) );
        assertThat( service.shouldRunScheduledLoop() ).isFalse();

        clock.set( Instant.parse( "2030-01-01T00:00:01Z" ) );
        assertThat( service.shouldRunScheduledLoop() ).isTrue();

        service.update( new EngineRuntimeControlRequest( true, 2_500L ) );
        assertThat( service.executionLoopIntervalMs() ).isEqualTo( 2_500L );
        assertThat( service.shouldRunScheduledLoop() ).isTrue();
    }

    @Test
    void exposesUpdatedAtAndRefreshesItOnRuntimeChanges()
    {
        // REQ: ENG-RTC-001
        // REQ: ENG-RTC-002
        EngineProperties properties = new EngineProperties();
        properties.setExecutionLoopEnabled( true );
        properties.setExecutionLoopIntervalMs( 1_000L );
        EngineTelemetryService telemetryService = new EngineTelemetryService();
        MutableClock clock = new MutableClock( Instant.parse( "2030-01-01T00:00:00Z" ) );
        EngineRuntimeControlService service = new EngineRuntimeControlService( properties, telemetryService, clock );

        assertThat( service.updatedAt() ).isEqualTo( Instant.parse( "2030-01-01T00:00:00Z" ) );

        clock.set( Instant.parse( "2030-01-01T00:05:00Z" ) );
        service.update( new EngineRuntimeControlRequest( null, null ) );

        assertThat( service.updatedAt() ).isEqualTo( Instant.parse( "2030-01-01T00:05:00Z" ) );
        assertThat( service.snapshot().runtimeUpdatedAt() ).isEqualTo( Instant.parse( "2030-01-01T00:05:00Z" ) );
    }

    @Test
    void returnsFalseWhenAnotherDispatcherWinsCompareAndSet()
    {
        // REQ: ENG-RTC-002
        EngineProperties properties = new EngineProperties();
        properties.setExecutionLoopEnabled( true );
        properties.setExecutionLoopIntervalMs( 1_000L );
        EngineTelemetryService telemetryService = new EngineTelemetryService();
        EngineRuntimeControlService service = new EngineRuntimeControlService(
            properties,
            telemetryService,
            Clock.fixed( Instant.parse( "2030-01-01T00:00:00Z" ), ZoneOffset.UTC )
        )
        {
            @Override
            boolean tryMarkScheduledDispatch( long previous, long now )
            {
                return false;
            }
        };

        assertThat( service.shouldRunScheduledLoop() ).isFalse();
    }

    @Test
    void compareAndSetHelperReturnsFalseWhenExpectedValueIsStale()
    {
        // REQ: ENG-RTC-002
        EngineProperties properties = new EngineProperties();
        properties.setExecutionLoopEnabled( true );
        properties.setExecutionLoopIntervalMs( 1_000L );
        EngineTelemetryService telemetryService = new EngineTelemetryService();
        EngineRuntimeControlService service = new EngineRuntimeControlService(
            properties,
            telemetryService,
            Clock.fixed( Instant.parse( "2030-01-01T00:00:00Z" ), ZoneOffset.UTC )
        );

        assertThat( service.tryMarkScheduledDispatch( Long.MIN_VALUE, 1_000L ) ).isTrue();
        assertThat( service.tryMarkScheduledDispatch( Long.MIN_VALUE, 2_000L ) ).isFalse();
    }

    @Test
    void leavesValuesUnchangedWhenUpdateRequestOmitsFields()
    {
        // REQ: ENG-RTC-002
        EngineProperties properties = new EngineProperties();
        properties.setExecutionLoopEnabled( true );
        properties.setExecutionLoopIntervalMs( 1_500L );
        EngineTelemetryService telemetryService = new EngineTelemetryService();
        EngineRuntimeControlService service = new EngineRuntimeControlService(
            properties,
            telemetryService,
            Clock.fixed( Instant.parse( "2030-01-01T00:00:00Z" ), ZoneOffset.UTC )
        );

        var response = service.update( new EngineRuntimeControlRequest( null, null ) );

        assertThat( response.executionLoopEnabled() ).isTrue();
        assertThat( response.executionLoopIntervalMs() ).isEqualTo( 1_500L );
    }

    @Test
    void exposesLiveSafetyConfigurationInRuntimeSnapshot()
    {
        EngineProperties properties = new EngineProperties();
        properties.setTradingVenueAccessMode( "production" );
        properties.setLiveOrderEnabled( true );
        properties.setKillSwitchEnabled( false );
        properties.setLiveEnabledVenues( "bybit,gate" );
        properties.setMaxNotionalUsd( new java.math.BigDecimal( "25" ) );
        EngineRuntimeControlService service = new EngineRuntimeControlService(
            properties,
            new EngineTelemetryService(),
            Clock.fixed( Instant.parse( "2030-01-01T00:00:00Z" ), ZoneOffset.UTC )
        );

        var response = service.snapshot();

        assertThat( response.tradingVenueAccessMode() ).isEqualTo( "production" );
        assertThat( response.liveOrderEnabled() ).isTrue();
        assertThat( response.killSwitchEnabled() ).isFalse();
        assertThat( response.liveEnabledVenues() ).containsExactly( "bybit", "gate" );
        assertThat( response.maxNotionalUsd() ).isEqualByComparingTo( "25" );
    }

    @Test
    void refusesScheduledLoopWhenRuntimeGateIsDisabled()
    {
        // REQ: ENG-RTC-002
        EngineProperties properties = new EngineProperties();
        properties.setExecutionLoopEnabled( false );
        properties.setExecutionLoopIntervalMs( 1_000L );
        EngineTelemetryService telemetryService = new EngineTelemetryService();
        EngineRuntimeControlService service = new EngineRuntimeControlService(
            properties,
            telemetryService,
            Clock.fixed( Instant.parse( "2030-01-01T00:00:00Z" ), ZoneOffset.UTC )
        );

        assertThat( service.shouldRunScheduledLoop() ).isFalse();
    }

    private static final class MutableClock extends Clock
    {
        private final AtomicReference<Instant> instant;

        private MutableClock( Instant initial )
        {
            this.instant = new AtomicReference<>( initial );
        }

        @Override
        public ZoneOffset getZone()
        {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone( java.time.ZoneId zone )
        {
            return this;
        }

        @Override
        public Instant instant()
        {
            return instant.get();
        }

        private void set( Instant next )
        {
            instant.set( next );
        }
    }
}
