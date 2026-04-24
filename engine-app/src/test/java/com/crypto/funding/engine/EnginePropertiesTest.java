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

        assertThat( properties.getMonitorBaseUrl() ).isEqualTo( "http://monitor.internal:18090" );
        assertThat( properties.getInternalToken() ).isEqualTo( "secret-token" );
        assertThat( properties.isExecutionLoopEnabled() ).isTrue();
    }
}
