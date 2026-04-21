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
    "spring.datasource.url=jdbc:sqlite:./build/test-internal-engine-metrics-disabled.sqlite",
    "spring.datasource.driver-class-name=org.sqlite.JDBC",
    "spring.datasource.hikari.maximum-pool-size=1",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
    "trading.candidate-source.enabled=false",
    "trading.metadata.sync-on-startup=false",
    "trading.metadata.schedule-enabled=false",
    "trading.metadata.require-credentials-on-startup=false",
    "security.operators.auth-enabled=false",
    "security.operators.internal-token=test-internal-token",
    "monitor.engine-metrics.enabled=false"
})
class InternalEngineMetricsDisabledIntegrationTest
{
    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void keepsMetricsEndpointAbsentWhenFeatureDisabled()
    {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType( MediaType.APPLICATION_JSON );
        headers.set( "X-Internal-Token", "test-internal-token" );

        ResponseEntity<String> response = restTemplate.exchange(
            url( "/internal/v1/engine/metrics-snapshot" ),
            HttpMethod.POST,
            new HttpEntity<>( snapshot(), headers ),
            String.class
        );

        assertThat( response.getStatusCode() ).isEqualTo( HttpStatus.NOT_FOUND );
    }

    private EngineMetricsSnapshot snapshot()
    {
        return new EngineMetricsSnapshot(
            "engine-app",
            "2.0.0",
            Instant.parse( "2030-01-01T00:00:00Z" ),
            true,
            false,
            1,
            0,
            Map.of( EnginePlanStatus.WAITING_ENTRY, 1L ),
            Map.of( "bybit", 1L ),
            Map.of(),
            0L,
            0L,
            0L,
            0L,
            0L,
            0L,
            0L,
            0L,
            0L,
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of()
        );
    }

    private String url( String path )
    {
        return "http://localhost:" + port + path;
    }
}
