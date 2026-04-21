package com.crypto.funding.api;

import com.crypto.funding.contract.engine.EngineMetricsSnapshot;
import com.crypto.funding.contract.engine.EnginePlanStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:sqlite:./build/test-internal-engine-metrics-enabled.sqlite",
    "spring.datasource.driver-class-name=org.sqlite.JDBC",
    "spring.datasource.hikari.maximum-pool-size=1",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
    "trading.candidate-source.enabled=false",
    "trading.metadata.sync-on-startup=false",
    "trading.metadata.schedule-enabled=false",
    "trading.metadata.require-credentials-on-startup=false",
    "security.operators.auth-enabled=false",
    "security.operators.internal-token=test-internal-token",
    "monitor.engine-metrics.enabled=true",
    "management.endpoints.web.exposure.include=health,info,prometheus",
    "management.prometheus.metrics.export.enabled=true"
})
class InternalEngineMetricsApiIntegrationTest
{
    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void protectsMetricsIngestAndExportsPrometheusView()
    {
        ResponseEntity<String> unauthorized = restTemplate.postForEntity(
            url( "/internal/v1/engine/metrics-snapshot" ),
            snapshot(),
            String.class
        );

        assertThat( unauthorized.getStatusCode() ).isEqualTo( HttpStatus.UNAUTHORIZED );
        assertThat( unauthorized.getBody() ).contains( "Valid X-Internal-Token is required." );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType( MediaType.APPLICATION_JSON );
        headers.set( "X-Internal-Token", "test-internal-token" );

        ResponseEntity<Void> accepted = restTemplate.exchange(
            url( "/internal/v1/engine/metrics-snapshot" ),
            HttpMethod.POST,
            new HttpEntity<>( snapshot(), headers ),
            Void.class
        );

        assertThat( accepted.getStatusCode() ).isEqualTo( HttpStatus.ACCEPTED );

        String prometheus = restTemplate.getForObject( url( "/actuator/prometheus" ), String.class );
        assertThat( prometheus ).contains( "funding_engine_up 1.0" );
        assertThat( prometheus ).contains( "funding_engine_plans 12.0" );
        assertThat( prometheus ).contains( "funding_engine_actionable_plans 3.0" );
        assertThat( prometheus ).contains( "funding_engine_plan_status{status=\"WAITING_ENTRY\"} 8.0" );
        assertThat( prometheus ).contains( "funding_engine_plan_status{status=\"ENTRY_WINDOW\"} 2.0" );
        assertThat( prometheus ).contains( "funding_engine_plan_status{status=\"EXIT_WINDOW\"} 1.0" );
    }

    private EngineMetricsSnapshot snapshot()
    {
        return new EngineMetricsSnapshot(
            "engine-app",
            "2.0.0",
            Instant.parse( "2030-01-01T00:00:00Z" ),
            true,
            false,
            12,
            3,
            Map.of(
                EnginePlanStatus.WAITING_ENTRY, 8L,
                EnginePlanStatus.ENTRY_WINDOW, 2L,
                EnginePlanStatus.EXIT_WINDOW, 1L,
                EnginePlanStatus.WAITING_EXIT, 1L
            )
        );
    }

    private String url( String path )
    {
        return "http://localhost:" + port + path;
    }
}
