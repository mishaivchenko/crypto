package com.crypto.funding.infrastructure.exchange.okx;

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

class OkxOrderBookAdapterTest
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
    void fetchesOrderBookFromBooksEndpoint()
        throws Exception
    {
        server.start();
        server.stubFor( get( urlPathEqualTo( "/api/v5/market/books" ) )
            .willReturn( okJson( """
                {
                  "code": "0",
                  "data": [{
                    "asks": [["50001.0", "2.5", "0", "3"]],
                    "bids": [["50000.0", "1.0", "0", "2"],
                             ["49999.0", "4.0", "0", "5"]],
                    "ts": "1700000000000"
                  }]
                }
                """ ) ) );

        OkxOrderBookAdapter adapter = new OkxOrderBookAdapter(
            HttpClient.newHttpClient(),
            new VenueHttpProperties(),
            venueProfileService( server.baseUrl() )
        );

        OrderBookSnapshot snapshot = adapter.fetchOrderBook( "BTC-USDT-SWAP", 20 );

        assertThat( snapshot.venue() ).isEqualTo( "okx" );
        assertThat( snapshot.symbol() ).isEqualTo( "BTC-USDT-SWAP" );
        assertThat( snapshot.bids() ).hasSize( 2 );
        assertThat( snapshot.asks() ).hasSize( 1 );
        assertThat( snapshot.bids().get( 0 ).price() ).isEqualByComparingTo( "50000.0" );
        assertThat( snapshot.bids().get( 0 ).quantity() ).isEqualByComparingTo( "1.0" );
        assertThat( snapshot.asks().get( 0 ).price() ).isEqualByComparingTo( "50001.0" );
        assertThat( snapshot.asks().get( 0 ).quantity() ).isEqualByComparingTo( "2.5" );
    }

    @Test
    void returnsEmptySnapshotWhenDataArrayEmpty()
        throws Exception
    {
        server.start();
        server.stubFor( get( urlPathEqualTo( "/api/v5/market/books" ) )
            .willReturn( okJson( """
                {"code": "0", "data": []}
                """ ) ) );

        OkxOrderBookAdapter adapter = new OkxOrderBookAdapter(
            HttpClient.newHttpClient(),
            new VenueHttpProperties(),
            venueProfileService( server.baseUrl() )
        );

        OrderBookSnapshot snapshot = adapter.fetchOrderBook( "UNKNOWN-USDT-SWAP", 20 );

        assertThat( snapshot.bids() ).isEmpty();
        assertThat( snapshot.asks() ).isEmpty();
    }

    @Test
    void throwsOnOkxApiErrorCode()
    {
        server.start();
        server.stubFor( get( urlPathEqualTo( "/api/v5/market/books" ) )
            .willReturn( okJson( """
                {"code": "51001", "msg": "Instrument ID does not exist"}
                """ ) ) );

        OkxOrderBookAdapter adapter = new OkxOrderBookAdapter(
            HttpClient.newHttpClient(),
            new VenueHttpProperties(),
            venueProfileService( server.baseUrl() )
        );

        assertThatThrownBy( () -> adapter.fetchOrderBook( "BAD-USDT-SWAP", 20 ) )
            .isInstanceOf( Exception.class )
            .hasMessageContaining( "51001" );
    }

    @Test
    void throwsOnHttpErrorStatus()
    {
        server.start();
        server.stubFor( get( urlPathEqualTo( "/api/v5/market/books" ) )
            .willReturn( aResponse().withStatus( 503 ).withBody( "unavailable" ) ) );

        OkxOrderBookAdapter adapter = new OkxOrderBookAdapter(
            HttpClient.newHttpClient(),
            new VenueHttpProperties(),
            venueProfileService( server.baseUrl() )
        );

        assertThatThrownBy( () -> adapter.fetchOrderBook( "BTC-USDT-SWAP", 20 ) )
            .isInstanceOf( Exception.class );
    }

    private VenueProfileService venueProfileService( String baseUrl )
    {
        VenueProfileService mock = mock( VenueProfileService.class );
        when( mock.resolveProductionBaseUrl( "okx" ) ).thenReturn( baseUrl );
        return mock;
    }
}
