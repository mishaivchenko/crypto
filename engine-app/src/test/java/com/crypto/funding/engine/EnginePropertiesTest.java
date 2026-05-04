package com.crypto.funding.engine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EnginePropertiesTest
{
    // REQ: ENG-CLI-005
    @Test
    void exposesSafeDefaultsForMonitorBaseUrlTokenAndLoop()
    {
        EngineProperties properties = new EngineProperties();

        assertThat( properties.getMonitorBaseUrl() ).isEqualTo( "http://localhost:8090" );
        assertThat( properties.getInternalToken() ).isEmpty();
        assertThat( properties.isExecutionLoopEnabled() ).isFalse();
        assertThat( properties.getExecutionLoopIntervalMs() ).isEqualTo( 1000L );
        assertThat( properties.isLiveOrderEnabled() ).isFalse();
        assertThat( properties.isKillSwitchEnabled() ).isTrue();
        assertThat( properties.getLiveEnabledVenues() ).isEqualTo( "bybit,gate" );
        assertThat( properties.liveEnabledVenues() ).containsExactly( "bybit", "gate" );
        assertThat( properties.getMaxNotionalUsd() ).isEqualByComparingTo( "25" );
        assertThat( properties.getMetadataMaxAgeMinutes() ).isEqualTo( 240L );
        assertThat( properties.getLatencyMaxAgeMinutes() ).isEqualTo( 1440L );
        assertThat( properties.getTradingVenueAccessMode() ).isEqualTo( "testnet" );
    }

    // REQ: ENG-RTC-001
    @Test
    void clampsExecutionLoopIntervalToMinimum()
    {
        EngineProperties properties = new EngineProperties();

        properties.setExecutionLoopIntervalMs( 10L );

        assertThat( properties.getExecutionLoopIntervalMs() ).isEqualTo( 100L );
    }

    // REQ: ENG-CLI-005
    @Test
    void storesExplicitBaseUrlTokenAndLoopToggle()
    {
        EngineProperties properties = new EngineProperties();

        properties.setMonitorBaseUrl( "http://monitor.internal:18090" );
        properties.setInternalToken( "secret-token" );
        properties.setExecutionLoopEnabled( true );
        properties.setLiveOrderEnabled( true );
        properties.setKillSwitchEnabled( false );
        properties.setLiveEnabledVenues( "bybit, , gate, bybit" );
        properties.setMaxNotionalUsd( new java.math.BigDecimal( "10" ) );
        properties.setMetadataMaxAgeMinutes( 0L );
        properties.setLatencyMaxAgeMinutes( -5L );
        properties.setTradingVenueAccessMode( "production" );

        assertThat( properties.getMonitorBaseUrl() ).isEqualTo( "http://monitor.internal:18090" );
        assertThat( properties.getInternalToken() ).isEqualTo( "secret-token" );
        assertThat( properties.isExecutionLoopEnabled() ).isTrue();
        assertThat( properties.isLiveOrderEnabled() ).isTrue();
        assertThat( properties.isKillSwitchEnabled() ).isFalse();
        assertThat( properties.getLiveEnabledVenues() ).isEqualTo( "bybit, , gate, bybit" );
        assertThat( properties.liveEnabledVenues() ).containsExactly( "bybit", "gate" );
        assertThat( properties.getMaxNotionalUsd() ).isEqualByComparingTo( "10" );
        assertThat( properties.getMetadataMaxAgeMinutes() ).isEqualTo( 1L );
        assertThat( properties.getLatencyMaxAgeMinutes() ).isEqualTo( 1L );
        assertThat( properties.getTradingVenueAccessMode() ).isEqualTo( "production" );
    }

    // REQ: ENG-CLI-005
    @Test
    void normalizesNullLiveOrderPropertiesToSafeFallbacks()
    {
        EngineProperties properties = new EngineProperties();

        properties.setLiveEnabledVenues( null );
        properties.setMaxNotionalUsd( null );
        properties.setTradingVenueAccessMode( "   " );

        assertThat( properties.getLiveEnabledVenues() ).isEmpty();
        assertThat( properties.liveEnabledVenues() ).isEmpty();
        assertThat( properties.getMaxNotionalUsd() ).isEqualByComparingTo( "25" );
        assertThat( properties.getTradingVenueAccessMode() ).isEqualTo( "testnet" );
    }
}
