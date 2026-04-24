package com.crypto.funding.engine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EngineMetricsPublishPropertiesTest
{
    // REQ: ENG-PUB-002
    @Test
    void exposesDisabledDefaultsAndClampsPublishInterval()
    {
        EngineMetricsPublishProperties properties = new EngineMetricsPublishProperties();

        assertThat( properties.isEnabled() ).isFalse();
        assertThat( properties.getIntervalMs() ).isEqualTo( 15000L );

        properties.setIntervalMs( 10L );

        assertThat( properties.getIntervalMs() ).isEqualTo( 1000L );
    }

    // REQ: ENG-PUB-002
    @Test
    void storesExplicitEnabledFlag()
    {
        EngineMetricsPublishProperties properties = new EngineMetricsPublishProperties();

        properties.setEnabled( true );

        assertThat( properties.isEnabled() ).isTrue();
    }
}
