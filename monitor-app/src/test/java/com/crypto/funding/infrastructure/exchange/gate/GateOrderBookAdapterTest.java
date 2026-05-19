package com.crypto.funding.infrastructure.exchange.gate;

import com.crypto.funding.config.VenueHttpProperties;
import com.crypto.funding.domain.liquidity.OrderBookSnapshot;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GateOrderBookAdapterTest
{
    private final WireMockServer server = new WireMockServer( options().dynamicPort() );

    @AfterEach
    void stop()
    {
        if( server.isRunning() )
        {
            server.stop();
        }
    }

    @Test
    void fetchesOrderBookFromFuturesEndpoint()
        throws Exception
    {
        server.start();
        server.stubFor( get( urlPathEqualTo( "/futures/usdt/order_book" ) )
            .willReturn( okJson( """
                {
                  "current": 1700000000.0,
                  "update": 1700000000.0,
                  "asks": [
                    {"p": "50001.0", "s": 100},
                    {"p": "50002.0", "s": 200}
                  ],
                  "bids": [
                    {"p": "50000.0", "s": 150}
                  ]
                }
                """ ) ) );

        GateOrderBookAdapter adapter = new GateOrderBookAdapter(
            HttpClient.newHttpClient(),
            new VenueHttpProperties(),
            server.baseUrl()
        );

        OrderBookSnapshot snapshot = adapter.fetchOrderBook( "BTC_USDT", 20 );

        assertThat( snapshot.venue() ).isEqualTo( "gate" );
        assertThat( snapshot.symbol() ).isEqualTo( "BTC_USDT" );
        assertThat( snapshot.bids() ).hasSize( 1 );
        assertThat( snapshot.asks() ).hasSize( 2 );
        // Gate contracts are USD-denominated: qty = sizeContracts / price
        assertThat( snapshot.bids().get( 0 ).price() ).isEqualByComparingTo( "50000.0" );
        assertThat( snapshot.bids().get( 0 ).quantity() ).isPositive();
        assertThat( snapshot.asks().get( 0 ).price() ).isEqualByComparingTo( "50001.0" );
    }

    @Test
    void throwsOnHttpErrorStatus()
    {
        server.start();
        server.stubFor( get( urlPathEqualTo( "/futures/usdt/order_book" ) )
            .willReturn( aResponse().withStatus( 429 ).withBody( "rate limited" ) ) );

        GateOrderBookAdapter adapter = new GateOrderBookAdapter(
            HttpClient.newHttpClient(),
            new VenueHttpProperties(),
            server.baseUrl()
        );

        assertThatThrownBy( () -> adapter.fetchOrderBook( "BTC_USDT", 20 ) )
            .isInstanceOf( Exception.class );
    }

    @Test
    void skipsLevelsWithZeroSize()
        throws Exception
    {
        server.start();
        server.stubFor( get( urlPathEqualTo( "/futures/usdt/order_book" ) )
            .willReturn( okJson( """
                {
                  "asks": [{"p": "50001.0", "s": 0}, {"p": "50002.0", "s": 50}],
                  "bids": []
                }
                """ ) ) );

        GateOrderBookAdapter adapter = new GateOrderBookAdapter(
            HttpClient.newHttpClient(),
            new VenueHttpProperties(),
            server.baseUrl()
        );

        OrderBookSnapshot snapshot = adapter.fetchOrderBook( "BTC_USDT", 20 );

        assertThat( snapshot.asks() ).hasSize( 1 );
        assertThat( snapshot.asks().get( 0 ).price() ).isEqualByComparingTo( "50002.0" );
    }
}
