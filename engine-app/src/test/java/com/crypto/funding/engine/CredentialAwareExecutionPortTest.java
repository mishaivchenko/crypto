package com.crypto.funding.engine;

import com.crypto.funding.domain.execution.ExecutionType;
import com.crypto.funding.domain.execution.OrderAttemptStatus;
import com.crypto.funding.domain.execution.OrderIntent;
import com.crypto.funding.contract.engine.EngineExecutionPlan;
import com.crypto.funding.contract.engine.EnginePlanStatus;
import com.crypto.funding.domain.trade.ArmedTradeState;
import com.crypto.funding.domain.trade.TradeSide;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

class CredentialAwareExecutionPortTest
{
    private WireMockServer exchange;

    @BeforeEach
    void startExchange()
    {
        exchange = new WireMockServer( 0 );
        exchange.start();
    }

    @AfterEach
    void stopExchange()
    {
        exchange.stop();
    }

    // REQ: ENG-CRED-001
    // REQ: ENG-CRED-003
    @Test
    void failsWithNormalizedVenueWhenApiKeyOrSecretIsMissing()
    {
        CredentialAwareExecutionPort port = new CredentialAwareExecutionPort( new MockEnvironment() );

        var attempt = port.submitOrder( plan( " ByBit " ), marketIntent(), false );

        assertThat( attempt.status() ).isEqualTo( OrderAttemptStatus.FAILED );
        assertThat( attempt.venue() ).isEqualTo( "bybit" );
        assertThat( attempt.failureReason() ).contains( "Missing engine credentials for bybit." );
        assertThat( attempt.failureReason() ).contains( "engine.credentials.bybit.api-key" );
        assertThat( attempt.failureReason() ).contains( "engine.credentials.bybit.secret-key" );
    }

    // REQ: ENG-CRED-002
    @ParameterizedTest
    @ValueSource(strings = { "bitget", "okx", "kucoin" })
    void requiresPassphraseOnlyForSpecificVenues( String venue )
    {
        MockEnvironment environment = new MockEnvironment()
            .withProperty( "engine.credentials." + venue + ".api-key", "key" )
            .withProperty( "engine.credentials." + venue + ".secret-key", "secret" );
        CredentialAwareExecutionPort port = new CredentialAwareExecutionPort( environment );

        var attempt = port.submitOrder( plan( venue ), marketIntent(), false );

        assertThat( attempt.status() ).isEqualTo( OrderAttemptStatus.FAILED );
        assertThat( attempt.failureReason() ).contains( "Missing engine passphrase for " + venue + "." );
        assertThat( attempt.failureReason() ).contains( "engine.credentials." + venue + ".passphrase" );
    }

    // REQ: ENG-CRED-002
    // REQ: ENG-CRED-004
    @Test
    void remainsGuardedWhenNonPassphraseVenueHasCredentials()
    {
        MockEnvironment environment = new MockEnvironment()
            .withProperty( "engine.credentials.bybit.api-key", "key" )
            .withProperty( "engine.credentials.bybit.secret-key", "secret" );
        CredentialAwareExecutionPort port = new CredentialAwareExecutionPort( environment );

        var attempt = port.submitOrder( plan( "bybit" ), marketIntent(), false );

        assertThat( attempt.status() ).isEqualTo( OrderAttemptStatus.FAILED );
        assertThat( attempt.failureReason() ).contains( "live order adapters are still guarded" );
    }

    // REQ: ENG-CRED-002
    // REQ: ENG-CRED-004
    @Test
    void remainsGuardedWhenPassphraseVenueHasAllCredentials()
    {
        MockEnvironment environment = new MockEnvironment()
            .withProperty( "engine.credentials.okx.api-key", "key" )
            .withProperty( "engine.credentials.okx.secret-key", "secret" )
            .withProperty( "engine.credentials.okx.passphrase", "passphrase" );
        CredentialAwareExecutionPort port = new CredentialAwareExecutionPort( environment );

        var attempt = port.submitOrder( plan( " OKX " ), marketIntent(), false );

        assertThat( attempt.status() ).isEqualTo( OrderAttemptStatus.FAILED );
        assertThat( attempt.venue() ).isEqualTo( "okx" );
        assertThat( attempt.failureReason() ).contains( "live order adapters are still guarded" );
    }

    // REQ: ENG-CRED-001
    @Test
    void treatsBlankCredentialsAsMissing()
    {
        MockEnvironment environment = new MockEnvironment()
            .withProperty( "engine.credentials.bybit.api-key", " " )
            .withProperty( "engine.credentials.bybit.secret-key", "secret" );
        CredentialAwareExecutionPort port = new CredentialAwareExecutionPort( environment );

        var attempt = port.submitOrder( plan( "bybit" ), marketIntent(), false );

        assertThat( attempt.status() ).isEqualTo( OrderAttemptStatus.FAILED );
        assertThat( attempt.failureReason() ).contains( "Missing engine credentials for bybit." );
    }

    // REQ: ENG-CRED-002
    @Test
    void treatsBlankPassphraseAsMissingForPassphraseVenue()
    {
        MockEnvironment environment = new MockEnvironment()
            .withProperty( "engine.credentials.okx.api-key", "key" )
            .withProperty( "engine.credentials.okx.secret-key", "secret" )
            .withProperty( "engine.credentials.okx.passphrase", " " );
        CredentialAwareExecutionPort port = new CredentialAwareExecutionPort( environment );

        var attempt = port.submitOrder( plan( "okx" ), marketIntent(), false );

        assertThat( attempt.status() ).isEqualTo( OrderAttemptStatus.FAILED );
        assertThat( attempt.failureReason() ).contains( "Missing engine passphrase for okx." );
    }

    // REQ: ENG-CRED-001
    @Test
    void treatsBlankSecretAsMissing()
    {
        MockEnvironment environment = new MockEnvironment()
            .withProperty( "engine.credentials.bybit.api-key", "key" )
            .withProperty( "engine.credentials.bybit.secret-key", " " );
        CredentialAwareExecutionPort port = new CredentialAwareExecutionPort( environment );

        var attempt = port.submitOrder( plan( "bybit" ), marketIntent(), false );

        assertThat( attempt.status() ).isEqualTo( OrderAttemptStatus.FAILED );
        assertThat( attempt.failureReason() ).contains( "Missing engine credentials for bybit." );
    }

    @Test
    void liveGateRejectsWhenLiveFlagIsOff()
    {
        MockEnvironment environment = liveEnvironment( "bybit" )
            .withProperty( "engine.live-order-enabled", "false" );
        CredentialAwareExecutionPort port = new CredentialAwareExecutionPort( environment );

        var attempt = port.submitOrder( livePlan( "bybit" ), marketIntent(), false );

        assertThat( attempt.status() ).isEqualTo( OrderAttemptStatus.FAILED );
        assertThat( attempt.failureReason() ).contains( "ENGINE_LIVE_ORDER_ENABLED=true" );
    }

    @Test
    void liveGateRejectsBurstEntriesForFirstProductionLoop()
    {
        MockEnvironment environment = liveEnvironment( "bybit" )
            .withProperty( "engine.live-order-enabled", "true" );
        CredentialAwareExecutionPort port = new CredentialAwareExecutionPort( environment );

        var attempt = port.submitOrder( livePlan( "bybit", 3 ), marketIntent(), false );

        assertThat( attempt.status() ).isEqualTo( OrderAttemptStatus.FAILED );
        assertThat( attempt.failureReason() ).contains( "entryAttemptCount must be 1" );
    }

    @Test
    void liveGateRejectsNonShortEntriesForFirstProductionLoop()
    {
        MockEnvironment environment = liveEnvironment( "bybit" )
            .withProperty( "engine.live-order-enabled", "true" );
        CredentialAwareExecutionPort port = new CredentialAwareExecutionPort( environment );

        var attempt = port.submitOrder(
            livePlan( "bybit" ),
            new OrderIntent( TradeSide.LONG, ExecutionType.MARKET, BigDecimal.valueOf( 25 ), null, null ),
            false
        );

        assertThat( attempt.status() ).isEqualTo( OrderAttemptStatus.FAILED );
        assertThat( attempt.failureReason() ).contains( "entry side must be SHORT" );
    }

    @Test
    void submitsBybitLinearMarketSellAndReadsFilledStatus()
    {
        exchange.stubFor( get( urlEqualTo( "/v5/market/tickers?category=linear&symbol=REQUSDT" ) ).willReturn( okJson( """
            {"retCode":0,"retMsg":"OK","result":{"list":[{"symbol":"REQUSDT","lastPrice":"2.5"}]}}
            """ ) ) );
        exchange.stubFor( post( "/v5/order/create" ).willReturn( okJson( """
            {"retCode":0,"retMsg":"OK","result":{"orderId":"bybit-order-1"}}
            """ ) ) );
        exchange.stubFor( get( urlEqualTo( "/v5/order/realtime?category=linear&symbol=REQUSDT&orderId=bybit-order-1&openOnly=1" ) )
            .willReturn( okJson( """
                {"retCode":0,"retMsg":"OK","result":{"list":[{"orderId":"bybit-order-1","orderStatus":"Filled","avgPrice":"2.5","cumExecQty":"10","cumExecFee":"0.01","updatedTime":"1893456000123"}]}}
                """ ) ) );
        CredentialAwareExecutionPort port = new CredentialAwareExecutionPort(
            liveEnvironment( "bybit" )
                .withProperty( "engine.live-order-enabled", "true" )
                .withProperty( "engine.bybit.testnet-base-url", exchange.baseUrl() )
        );

        var attempt = port.submitOrder( liveEntryPlanWithoutPositionPrice( "bybit" ), marketIntent(), false );

        assertThat( attempt.status() ).isEqualTo( OrderAttemptStatus.FILLED );
        assertThat( attempt.externalOrderId() ).isEqualTo( "bybit-order-1" );
        assertThat( attempt.quantity() ).isEqualByComparingTo( "10" );
        assertThat( attempt.averageFillPrice() ).isEqualByComparingTo( "2.5" );
        exchange.verify( postRequestedFor( urlEqualTo( "/v5/order/create" ) )
            .withRequestBody( containing( "\"category\":\"linear\"" ) )
            .withRequestBody( containing( "\"symbol\":\"REQUSDT\"" ) )
            .withRequestBody( containing( "\"side\":\"Sell\"" ) )
            .withRequestBody( containing( "\"orderType\":\"Market\"" ) )
            .withRequestBody( containing( "\"reduceOnly\":false" ) ) );
    }

    @Test
    void submitsGateEntryUsingTickerAndContractMultiplier()
    {
        exchange.stubFor( get( urlEqualTo( "/futures/usdt/tickers?contract=REQ_USDT" ) ).willReturn( okJson( """
            [{"contract":"REQ_USDT","last":"2.5"}]
            """ ) ) );
        exchange.stubFor( get( urlEqualTo( "/futures/usdt/contracts/REQ_USDT" ) ).willReturn( okJson( """
            {"name":"REQ_USDT","quanto_multiplier":"0.01"}
            """ ) ) );
        exchange.stubFor( post( "/futures/usdt/orders" ).willReturn( okJson( """
            {"id":"gate-order-entry-1","status":"finished","finish_as":"filled","contract":"REQ_USDT","size":-1000,"fill_price":"2.5","fee":"0.01","create_time":1893456000}
            """ ) ) );
        CredentialAwareExecutionPort port = new CredentialAwareExecutionPort(
            liveEnvironment( "gate" )
                .withProperty( "engine.live-order-enabled", "true" )
                .withProperty( "engine.gate.testnet-base-url", exchange.baseUrl() )
        );

        var attempt = port.submitOrder( liveEntryPlanWithoutPositionPrice( "gate" ), marketIntent(), false );

        assertThat( attempt.status() ).isEqualTo( OrderAttemptStatus.FILLED );
        assertThat( attempt.externalOrderId() ).isEqualTo( "gate-order-entry-1" );
        assertThat( attempt.quantity() ).isEqualByComparingTo( "1000" );
        assertThat( attempt.averageFillPrice() ).isEqualByComparingTo( "2.5" );
        exchange.verify( postRequestedFor( urlEqualTo( "/futures/usdt/orders" ) )
            .withRequestBody( containing( "\"contract\":\"REQ_USDT\"" ) )
            .withRequestBody( containing( "\"size\":-1000" ) )
            .withRequestBody( containing( "\"price\":\"0\"" ) )
            .withRequestBody( containing( "\"tif\":\"ioc\"" ) )
            .withRequestBody( containing( "\"reduce_only\":false" ) ) );
    }

    // REQ: ENG-CRED-001
    @Test
    void liveGateRejectsWhenInstrumentMetadataIsStale()
    {
        MockEnvironment environment = liveEnvironment( "bybit" )
            .withProperty( "engine.live-order-enabled", "true" )
            .withProperty( "engine.metadata-max-age-minutes", "240" );
        CredentialAwareExecutionPort port = new CredentialAwareExecutionPort( environment );
        EngineExecutionPlan stale = livePlanWithTimings( "bybit", Instant.now().minusSeconds( 241 * 60 ), Instant.now() );

        var attempt = port.submitOrder( stale, marketIntent(), false );

        assertThat( attempt.status() ).isEqualTo( OrderAttemptStatus.FAILED );
        assertThat( attempt.failureReason() ).contains( "Instrument metadata is stale" );
    }

    // REQ: ENG-CRED-001
    @Test
    void liveGateRejectsWhenLatencyProfileIsStale()
    {
        MockEnvironment environment = liveEnvironment( "bybit" )
            .withProperty( "engine.live-order-enabled", "true" )
            .withProperty( "engine.latency-max-age-minutes", "1440" );
        CredentialAwareExecutionPort port = new CredentialAwareExecutionPort( environment );
        EngineExecutionPlan stale = livePlanWithTimings( "bybit", Instant.now(), Instant.now().minusSeconds( 1441 * 60 ) );

        var attempt = port.submitOrder( stale, marketIntent(), false );

        assertThat( attempt.status() ).isEqualTo( OrderAttemptStatus.FAILED );
        assertThat( attempt.failureReason() ).contains( "Latency profile is stale" );
    }

    @Test
    void submitsGateMarketReduceOnlyExit()
    {
        exchange.stubFor( post( "/futures/usdt/orders" ).willReturn( okJson( """
            {"id":"gate-order-1","status":"finished","finish_as":"filled","contract":"REQ_USDT","size":10,"fill_price":"2.5","fee":"0.01","create_time":1893456000}
            """ ) ) );
        CredentialAwareExecutionPort port = new CredentialAwareExecutionPort(
            liveEnvironment( "gate" )
                .withProperty( "engine.live-order-enabled", "true" )
                .withProperty( "engine.gate.testnet-base-url", exchange.baseUrl() )
        );

        var attempt = port.submitOrder( livePlan( "gate" ), new OrderIntent(
            TradeSide.LONG,
            ExecutionType.MARKET,
            BigDecimal.TEN,
            null,
            null
        ), true );

        assertThat( attempt.status() ).isEqualTo( OrderAttemptStatus.FILLED );
        assertThat( attempt.externalOrderId() ).isEqualTo( "gate-order-1" );
        assertThat( attempt.quantity() ).isEqualByComparingTo( "10" );
        assertThat( attempt.averageFillPrice() ).isEqualByComparingTo( "2.5" );
        exchange.verify( postRequestedFor( urlEqualTo( "/futures/usdt/orders" ) )
            .withRequestBody( containing( "\"contract\":\"REQ_USDT\"" ) )
            .withRequestBody( containing( "\"size\":10" ) )
            .withRequestBody( containing( "\"price\":\"0\"" ) )
            .withRequestBody( containing( "\"tif\":\"ioc\"" ) )
            .withRequestBody( containing( "\"reduce_only\":true" ) ) );
    }

    private static OrderIntent marketIntent()
    {
        return new OrderIntent( TradeSide.SHORT, ExecutionType.MARKET, BigDecimal.valueOf( 25 ), null, null );
    }

    private static MockEnvironment liveEnvironment( String venue )
    {
        return new MockEnvironment()
            .withProperty( "engine.live-enabled-venues", "bybit,gate" )
            .withProperty( "engine.kill-switch-enabled", "false" )
            .withProperty( "engine.max-notional-usd", "25" )
            .withProperty( "engine.metadata-max-age-minutes", "1440" )
            .withProperty( "engine.latency-max-age-minutes", "1440" )
            .withProperty( "engine.trading-venue-access-mode", "testnet" )
            .withProperty( "engine.credentials." + venue + ".api-key", "key" )
            .withProperty( "engine.credentials." + venue + ".secret-key", "secret" );
    }

    private static EngineExecutionPlan plan( String venue )
    {
        return livePlan( venue, 1 );
    }

    private static EngineExecutionPlan livePlan( String venue )
    {
        return livePlan( venue, 1 );
    }

    private static EngineExecutionPlan livePlan( String venue, Integer entryAttemptCount )
    {
        return livePlan( venue, entryAttemptCount, BigDecimal.valueOf( 2.5 ) );
    }

    private static EngineExecutionPlan liveEntryPlanWithoutPositionPrice( String venue )
    {
        return livePlan( venue, 1, null );
    }

    private static EngineExecutionPlan livePlan( String venue, Integer entryAttemptCount, BigDecimal positionEntryPrice )
    {
        return new EngineExecutionPlan(
            5L,
            10L,
            venue,
            "REQ/USDT",
            TradeSide.SHORT,
            BigDecimal.valueOf( 25 ),
            ArmedTradeState.ARMED,
            Instant.parse( "2030-01-01T00:00:00Z" ),
            Instant.parse( "2029-12-31T23:59:00Z" ),
            Instant.parse( "2030-01-01T00:01:00Z" ),
            entryAttemptCount,
            0L,
            25L,
            0L,
            25L,
            List.of(),
            EnginePlanStatus.ENTRY_WINDOW,
            Instant.parse( "2029-12-31T23:59:00Z" ),
            0L,
            60_000L,
            "summary",
            venue.trim().equalsIgnoreCase( "gate" ) ? "REQ_USDT" : "REQUSDT",
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.valueOf( 5 ),
            Instant.parse( "2029-12-31T00:00:00Z" ),
            Instant.parse( "2029-12-31T00:00:00Z" ),
            BigDecimal.TEN,
            positionEntryPrice
        );
    }

    private static EngineExecutionPlan livePlanWithTimings( String venue, Instant metadataLastSyncedAt, Instant latencySampledAt )
    {
        return new EngineExecutionPlan(
            5L,
            10L,
            venue,
            "REQ/USDT",
            TradeSide.SHORT,
            BigDecimal.valueOf( 25 ),
            ArmedTradeState.ARMED,
            Instant.parse( "2030-01-01T00:00:00Z" ),
            Instant.parse( "2029-12-31T23:59:00Z" ),
            Instant.parse( "2030-01-01T00:01:00Z" ),
            1,
            0L,
            25L,
            0L,
            25L,
            List.of(),
            EnginePlanStatus.ENTRY_WINDOW,
            Instant.parse( "2029-12-31T23:59:00Z" ),
            0L,
            60_000L,
            "summary",
            "REQUSDT",
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.valueOf( 5 ),
            metadataLastSyncedAt,
            latencySampledAt,
            BigDecimal.TEN,
            null
        );
    }
}
