package com.crypto.funding.infrastructure.exchange.bitget;

import com.crypto.funding.application.venue.VenueProfileService;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BitgetOrderBookAdapterTest
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
    void fetchesOrderBookFromCorrectEndpoint()
        throws Exception
    {
        server.start();
        server.stubFor( get( urlPathEqualTo( "/api/v2/mix/market/orderbook" ) )
            .willReturn( okJson( """
                {
                  "code": "00000",
                  "msg": "success",
                  "data": {
                    "asks": [["50000.0", "1.5"], ["50001.0", "2.0"]],
                    "bids": [["49999.0", "3.0"], ["49998.0", "1.0"]],
                    "ts": "1700000000000"
                  }
                }
                """ ) ) );

        BitgetOrderBookAdapter adapter = new BitgetOrderBookAdapter(
            HttpClient.newHttpClient(),
            new VenueHttpProperties(),
            venueProfileService( server.baseUrl() )
        );

        OrderBookSnapshot snapshot = adapter.fetchOrderBook( "BTCUSDT", 20 );

        assertThat( snapshot.venue() ).isEqualTo( "bitget" );
        assertThat( snapshot.symbol() ).isEqualTo( "BTCUSDT" );
        assertThat( snapshot.bids() ).hasSize( 2 );
        assertThat( snapshot.asks() ).hasSize( 2 );
        assertThat( snapshot.bids().get( 0 ).price() ).isEqualByComparingTo( "49999.0" );
        assertThat( snapshot.bids().get( 0 ).quantity() ).isEqualByComparingTo( "3.0" );
        assertThat( snapshot.asks().get( 0 ).price() ).isEqualByComparingTo( "50000.0" );
    }

    @Test
    void throwsOnBitgetApiErrorCode()
    {
        server.start();
        server.stubFor( get( urlPathEqualTo( "/api/v2/mix/market/orderbook" ) )
            .willReturn( okJson( """
                {"code": "40404", "msg": "Request URL NOT FOUND", "data": null}
                """ ) ) );

        BitgetOrderBookAdapter adapter = new BitgetOrderBookAdapter(
            HttpClient.newHttpClient(),
            new VenueHttpProperties(),
            venueProfileService( server.baseUrl() )
        );

        assertThatThrownBy( () -> adapter.fetchOrderBook( "BTCUSDT", 20 ) )
            .isInstanceOf( Exception.class )
            .hasMessageContaining( "40404" );
    }

    @Test
    void throwsOnHttpErrorStatus()
    {
        server.start();
        server.stubFor( get( urlPathEqualTo( "/api/v2/mix/market/orderbook" ) )
            .willReturn( aResponse().withStatus( 429 ).withBody( "rate limited" ) ) );

        BitgetOrderBookAdapter adapter = new BitgetOrderBookAdapter(
            HttpClient.newHttpClient(),
            new VenueHttpProperties(),
            venueProfileService( server.baseUrl() )
        );

        assertThatThrownBy( () -> adapter.fetchOrderBook( "BTCUSDT", 20 ) )
            .isInstanceOf( Exception.class );
    }

    @Test
    void clampsDepthat150()
        throws Exception
    {
        server.start();
        server.stubFor( get( urlPathEqualTo( "/api/v2/mix/market/orderbook" ) )
            .willReturn( okJson( """
                {"code": "00000", "data": {"asks": [], "bids": []}}
                """ ) ) );

        BitgetOrderBookAdapter adapter = new BitgetOrderBookAdapter(
            HttpClient.newHttpClient(),
            new VenueHttpProperties(),
            venueProfileService( server.baseUrl() )
        );

        adapter.fetchOrderBook( "BTCUSDT", 500 );

        server.verify( com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(
            com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo( "/api/v2/mix/market/orderbook" ) )
            .withQueryParam( "limit", com.github.tomakehurst.wiremock.client.WireMock.equalTo( "150" ) ) );
    }

    private VenueProfileService venueProfileService( String baseUrl )
    {
        VenueProfileService mock = mock( VenueProfileService.class );
        when( mock.resolveProductionBaseUrl( "bitget" ) ).thenReturn( baseUrl );
        return mock;
    }
}
