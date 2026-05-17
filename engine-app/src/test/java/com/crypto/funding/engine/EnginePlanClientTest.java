package com.crypto.funding.engine;

import com.crypto.funding.contract.engine.MarkPriceResponse;
import com.crypto.funding.contract.engine.EngineMetricsSnapshot;
import com.crypto.funding.contract.engine.EngineOrderAttemptRecordRequest;
import com.crypto.funding.contract.engine.EnginePlanStatus;
import com.crypto.funding.contract.engine.EnginePositionRecordRequest;
import com.crypto.funding.contract.engine.EngineTradeOutcomeRecordRequest;
import com.crypto.funding.contract.engine.EngineTradeStateUpdateRequest;
import com.crypto.funding.domain.execution.ExecutionType;
import com.crypto.funding.domain.execution.OrderAttemptStatus;
import com.crypto.funding.domain.trade.ArmedTradeState;
import com.crypto.funding.domain.trade.PositionState;
import com.crypto.funding.domain.trade.TradeSide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EnginePlanClientTest
{
    private EngineTelemetryService telemetryService;
    private EnginePlanClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp()
    {
        telemetryService = new EngineTelemetryService();
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo( builder ).build();
        client = new EnginePlanClient(
            builder,
            properties(),
            telemetryService,
            advancingNanos( 2_000_000_000L, 40_000_000L )
        );
    }

    // REQ: ENG-CLI-001
    @Test
    void sendsIncludeAllQueryParamAndInternalTokenWhenListingPlans()
    {
        server.expect( requestTo( "http://monitor.test/internal/v1/engine/plans?includeAll=true" ) )
              .andExpect( method( HttpMethod.GET ) )
              .andExpect( header( "X-Internal-Token", "internal-token" ) )
              .andRespond( withSuccess( "[]", MediaType.APPLICATION_JSON ) );

        List<?> plans = client.listPlans( true );

        assertThat( plans ).isEmpty();
        assertThat( telemetryService.snapshot().lastPlanFetchDurationMs() ).isGreaterThanOrEqualTo( 0L );
        server.verify();
    }

    // REQ: ENG-CLI-001
    @Test
    void listPlansDefaultsIncludeAllToFalse()
    {
        server.expect( requestTo( "http://monitor.test/internal/v1/engine/plans?includeAll=false" ) )
              .andExpect( method( HttpMethod.GET ) )
              .andExpect( header( "X-Internal-Token", "internal-token" ) )
              .andRespond( withSuccess( "[]", MediaType.APPLICATION_JSON ) );

        List<?> plans = client.listPlans();

        assertThat( plans ).isEmpty();
        assertThat( telemetryService.snapshot().lastPlanFetchDurationMs() ).isEqualTo( 40L );
        server.verify();
    }

    // REQ: ENG-CLI-002
    @Test
    void postsOrderAttemptsAndRecordsTiming()
    {
        server.expect( requestTo( "http://monitor.test/internal/v1/engine/order-attempts" ) )
              .andExpect( method( HttpMethod.POST ) )
              .andExpect( header( "X-Internal-Token", "internal-token" ) )
              .andExpect( jsonPath( "$.attemptKey" ).value( "entry:5:1:2030-01-01T00:00:00Z" ) )
              .andExpect( jsonPath( "$.status" ).value( "FAILED" ) )
              .andRespond( withSuccess( """
                  {
                    "id": 1,
                    "attemptKey": "entry:5:1:2030-01-01T00:00:00Z",
                    "armedTradeId": 5,
                    "attemptNumber": 1,
                    "venue": "bybit",
                    "symbol": "REQ/USDT",
                    "side": "SHORT",
                    "executionType": "MARKET",
                    "quantity": 25,
                    "status": "FAILED",
                    "submittedAt": "2030-01-01T00:00:01Z",
                    "createdAt": "2030-01-01T00:00:01Z",
                    "updatedAt": "2030-01-01T00:00:01Z"
                  }
                  """, MediaType.APPLICATION_JSON ) );

        var response = client.recordOrderAttempt( new EngineOrderAttemptRecordRequest(
            "entry:5:1:2030-01-01T00:00:00Z",
            5L,
            1,
            "bybit",
            "REQ/USDT",
            TradeSide.SHORT,
            ExecutionType.MARKET,
            BigDecimal.valueOf( 25 ),
            null,
            OrderAttemptStatus.FAILED,
            null,
            Instant.parse( "2030-01-01T00:00:00Z" ),
            Instant.parse( "2030-01-01T00:00:00Z" ),
            Instant.parse( "2030-01-01T00:00:01Z" ),
            null,
            "guarded"
        ) );

        assertThat( response.attemptKey() ).isEqualTo( "entry:5:1:2030-01-01T00:00:00Z" );
        assertThat( telemetryService.snapshot().lastAttemptRecordDurationMs() ).isEqualTo( 40L );
        server.verify();
    }

    // REQ: ENG-CLI-003
    @Test
    void publishesMetricsSnapshotWithInternalToken()
    {
        server.expect( requestTo( "http://monitor.test/internal/v1/engine/metrics-snapshot" ) )
              .andExpect( method( HttpMethod.POST ) )
              .andExpect( header( "X-Internal-Token", "internal-token" ) )
              .andExpect( content().contentTypeCompatibleWith( MediaType.APPLICATION_JSON ) )
              .andExpect( jsonPath( "$.module" ).value( "engine-app" ) )
              .andRespond( withSuccess() );

        client.publishMetricsSnapshot( snapshot() );

        server.verify();
    }

    // REQ: ENG-CLI-004
    @Test
    void fetchesSinglePlanById()
    {
        server.expect( requestTo( "http://monitor.test/internal/v1/engine/plans/5" ) )
              .andExpect( method( HttpMethod.GET ) )
              .andExpect( header( "X-Internal-Token", "internal-token" ) )
              .andRespond( withSuccess( """
                  {
                    "armedTradeId": 5,
                    "fundingEventId": 10,
                    "venue": "bybit",
                    "symbol": "REQ/USDT",
                    "intendedSide": "SHORT",
                    "notionalUsd": 25,
                    "tradeState": "ARMED",
                    "entryAttempts": [],
                    "status": "ENTRY_WINDOW",
                    "summary": "summary"
                  }
                  """, MediaType.APPLICATION_JSON ) );

        var plan = client.getPlan( 5L );

        assertThat( plan.armedTradeId() ).isEqualTo( 5L );
        assertThat( plan.venue() ).isEqualTo( "bybit" );
        server.verify();
    }

    // REQ: ENG-CLI-002
    @Test
    void postsPositionStateAndOutcomeLifecycleContracts()
    {
        server.expect( requestTo( "http://monitor.test/internal/v1/engine/positions" ) )
              .andExpect( method( HttpMethod.POST ) )
              .andExpect( header( "X-Internal-Token", "internal-token" ) )
              .andExpect( jsonPath( "$.armedTradeId" ).value( 5 ) )
              .andExpect( jsonPath( "$.state" ).value( "OPEN" ) )
              .andRespond( withSuccess( """
                  {
                    "id": 10,
                    "armedTradeId": 5,
                    "venue": "bybit",
                    "symbol": "REQ/USDT",
                    "side": "SHORT",
                    "quantity": 10,
                    "entryPrice": 2.5,
                    "state": "OPEN",
                    "openedAt": "2030-01-01T00:00:00Z",
                    "createdAt": "2030-01-01T00:00:00Z",
                    "updatedAt": "2030-01-01T00:00:00Z"
                  }
                  """, MediaType.APPLICATION_JSON ) );
        server.expect( requestTo( "http://monitor.test/internal/v1/engine/trades/5/state" ) )
              .andExpect( method( HttpMethod.POST ) )
              .andExpect( header( "X-Internal-Token", "internal-token" ) )
              .andExpect( jsonPath( "$.state" ).value( "OPEN" ) )
              .andRespond( withSuccess( """
                  {
                    "armedTradeId": 5,
                    "state": "OPEN",
                    "updatedAt": "2030-01-01T00:00:00Z"
                  }
                  """, MediaType.APPLICATION_JSON ) );
        server.expect( requestTo( "http://monitor.test/internal/v1/engine/outcomes" ) )
              .andExpect( method( HttpMethod.POST ) )
              .andExpect( header( "X-Internal-Token", "internal-token" ) )
              .andExpect( jsonPath( "$.outcomeCode" ).value( "CLOSED" ) )
              .andRespond( withSuccess( """
                  {
                    "id": 11,
                    "armedTradeId": 5,
                    "grossPnlUsd": 1.2,
                    "netPnlUsd": 1.18,
                    "feesUsd": 0.02,
                    "outcomeCode": "CLOSED",
                    "evaluatedAt": "2030-01-01T00:01:00Z",
                    "createdAt": "2030-01-01T00:01:00Z",
                    "updatedAt": "2030-01-01T00:01:00Z"
                  }
                  """, MediaType.APPLICATION_JSON ) );

        var position = client.recordPosition( new EnginePositionRecordRequest(
            5L,
            "bybit",
            "REQ/USDT",
            TradeSide.SHORT,
            BigDecimal.TEN,
            BigDecimal.valueOf( 2.5 ),
            null,
            PositionState.OPEN,
            Instant.parse( "2030-01-01T00:00:00Z" ),
            null
        ) );
        var state = client.updateTradeState( 5L, new EngineTradeStateUpdateRequest( ArmedTradeState.OPEN, "entry filled" ) );
        var outcome = client.recordTradeOutcome( new EngineTradeOutcomeRecordRequest(
            5L,
            BigDecimal.valueOf( 1.2 ),
            BigDecimal.valueOf( 1.18 ),
            BigDecimal.valueOf( 0.02 ),
            "CLOSED",
            "entry/exit filled",
            Instant.parse( "2030-01-01T00:01:00Z" )
        ) );

        assertThat( position.id() ).isEqualTo( 10L );
        assertThat( state.state() ).isEqualTo( ArmedTradeState.OPEN );
        assertThat( outcome.outcomeCode() ).isEqualTo( "CLOSED" );
        server.verify();
    }

    // REQ: ENG-CLI-005
    @Test
    void omitsInternalTokenHeaderWhenTokenIsBlank()
    {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer blankTokenServer = MockRestServiceServer.bindTo( builder ).build();
        EngineProperties properties = properties();
        properties.setInternalToken( "   " );
        EnginePlanClient blankTokenClient = new EnginePlanClient(
            builder,
            properties,
            new EngineTelemetryService(),
            advancingNanos( 3_000_000_000L, 40_000_000L )
        );

        blankTokenServer.expect( requestTo( "http://monitor.test/internal/v1/engine/plans?includeAll=false" ) )
                       .andExpect( method( HttpMethod.GET ) )
                       .andExpect( request -> assertThat( request.getHeaders().containsKey( "X-Internal-Token" ) ).isFalse() )
                       .andRespond( withSuccess( "[]", MediaType.APPLICATION_JSON ) );

        assertThat( blankTokenClient.listPlans() ).isEmpty();
        blankTokenServer.verify();
    }

    // REQ: ENG-CLI-005
    @Test
    void omitsInternalTokenHeaderWhenTokenIsNull()
    {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer nullTokenServer = MockRestServiceServer.bindTo( builder ).build();
        EngineProperties properties = properties();
        properties.setInternalToken( null );
        EnginePlanClient nullTokenClient = new EnginePlanClient(
            builder,
            properties,
            new EngineTelemetryService(),
            advancingNanos( 4_000_000_000L, 40_000_000L )
        );

        nullTokenServer.expect( requestTo( "http://monitor.test/internal/v1/engine/plans?includeAll=false" ) )
                       .andExpect( method( HttpMethod.GET ) )
                       .andExpect( request -> assertThat( request.getHeaders().containsKey( "X-Internal-Token" ) ).isFalse() )
                       .andRespond( withSuccess( "[]", MediaType.APPLICATION_JSON ) );

        assertThat( nullTokenClient.listPlans() ).isEmpty();
        nullTokenServer.verify();
    }

    // REQ: ENG-CLI-006
    @Test
    void fetchMarkPriceReturnsPriceOnSuccess()
    {
        server.expect( requestTo( "http://monitor.test/internal/v1/engine/mark-price?venue=bybit&symbol=REQUSDT" ) )
              .andExpect( method( HttpMethod.GET ) )
              .andExpect( header( "X-Internal-Token", "internal-token" ) )
              .andRespond( withSuccess( """
                  {
                    "venue": "bybit",
                    "symbol": "REQUSDT",
                    "markPrice": 2.50,
                    "fetchedAt": "2030-01-01T00:00:00Z"
                  }
                  """, MediaType.APPLICATION_JSON ) );

        Optional<MarkPriceResponse> result = client.fetchMarkPrice( "bybit", "REQUSDT" );

        assertThat( result ).isPresent();
        assertThat( result.get().markPrice() ).isEqualByComparingTo( "2.50" );
        server.verify();
    }

    // REQ: ENG-CLI-006
    @Test
    void fetchMarkPriceReturnsEmptyOnServerError()
    {
        server.expect( requestTo( "http://monitor.test/internal/v1/engine/mark-price?venue=bybit&symbol=REQUSDT" ) )
              .andExpect( method( HttpMethod.GET ) )
              .andRespond( withServerError() );

        Optional<MarkPriceResponse> result = client.fetchMarkPrice( "bybit", "REQUSDT" );

        assertThat( result ).isEmpty();
        server.verify();
    }

    private static EngineProperties properties()
    {
        EngineProperties properties = new EngineProperties();
        properties.setMonitorBaseUrl( "http://monitor.test" );
        properties.setInternalToken( "internal-token" );
        return properties;
    }

    private static EngineMetricsSnapshot snapshot()
    {
        return new EngineMetricsSnapshot(
            "engine-app",
            "2.0.0",
            Instant.parse( "2030-01-01T00:00:00Z" ),
            true,
            false,
            1000L,
            Instant.parse( "2030-01-01T00:00:10Z" ),
            1,
            1,
            Map.of( EnginePlanStatus.ENTRY_WINDOW, 1L ),
            Map.of( "bybit", 1L ),
            Map.of( "bybit", 1L ),
            1L,
            0L,
            1L,
            20L,
            20L,
            Instant.parse( "2030-01-01T00:00:00Z" ),
            Instant.parse( "2030-01-01T00:00:01Z" ),
            false,
            1,
            1,
            0,
            null,
            null,
            0,
            0,
            0,
            0L,
            10L,
            10L,
            5L,
            5L,
            Map.of( "failed", 1L ),
            Map.of( "bybit", 1L ),
            Map.of( "bybit", 1L ),
            Map.of( "bybit", 20L ),
            Map.of( "bybit", 20L )
        );
    }

    private static LongSupplier advancingNanos( long startingNanos, long stepNanos )
    {
        AtomicLong current = new AtomicLong( startingNanos );
        return () -> current.getAndAdd( stepNanos );
    }
}
