package com.crypto.funding.api;

import com.crypto.funding.infrastructure.telemetry.VenueRequestTimingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:sqlite:./build/test-venue-request-timing-metrics.sqlite",
    "spring.datasource.driver-class-name=org.sqlite.JDBC",
    "spring.datasource.hikari.maximum-pool-size=1",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
    "trading.candidate-source.enabled=false",
    "trading.metadata.sync-on-startup=false",
    "trading.metadata.schedule-enabled=false",
    "trading.metadata.require-credentials-on-startup=false",
    "security.operators.auth-enabled=false",
    "management.endpoints.web.exposure.include=health,info,prometheus",
    "management.prometheus.metrics.export.enabled=true"
})
class VenueRequestTimingMetricsIntegrationTest
{
    @LocalServerPort
    private int port;

    @Autowired
    private VenueRequestTimingService venueRequestTimingService;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void exportsVenueRequestTimingMetrics()
    {
        venueRequestTimingService.recordSuccess( "gate", "metadata-sync", 42_000_000L, 128L, 200 );
        venueRequestTimingService.recordFailure( "gate", "credential-check", 11_000_000L, "invalid credentials" );

        String prometheus = restTemplate.getForObject( url( "/actuator/prometheus" ), String.class );

        assertThat( prometheus ).contains( "funding_venue_request_avg_duration_ms{operation=\"metadata-sync\",venue=\"gate\"} 42.0" );
        assertThat( prometheus ).contains( "funding_venue_request_last_duration_ms{operation=\"credential-check\",venue=\"gate\"} 11.0" );
        assertThat( prometheus ).contains( "funding_venue_requests_total{operation=\"metadata-sync\",venue=\"gate\"} 1.0" );
        assertThat( prometheus ).contains( "funding_venue_request_failures_total{operation=\"credential-check\",venue=\"gate\"} 1.0" );
        assertThat( prometheus ).contains( "funding_venue_request_avg_duration_ms{operation=\"metadata-sync\",venue=\"bybit\"} 0.0" );
    }

    private String url( String path )
    {
        return "http://localhost:" + port + path;
    }
}
