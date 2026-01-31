package com.crypto.funding.exchanges;

import com.crypto.funding.exchanges.gate.GateFeignClient;
import com.crypto.funding.exchanges.gate.GateRestClient;
import com.crypto.funding.trading.OrderSide;
import com.crypto.funding.trading.OrderType;
import com.crypto.funding.trading.PlaceTestOrderCommand;
import com.crypto.funding.trading.TestOrderResult;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

class GateRestClientTest
{
    private WireMockServer server;

    @BeforeEach
    void start()
    {
        server = new WireMockServer( 0 );
        server.start();
        configureFor( "localhost", server.port() );
    }

    @AfterEach
    void stop()
    {
        server.stop();
    }

    @Test
    void placesMarketOrder() throws Exception
    {
        stubFor( post( urlEqualTo( "/futures/usdt/orders" ) )
                     .willReturn( aResponse()
                                      .withStatus( 200 )
                                      .withHeader( "Content-Type", "application/json" )
                                      .withBody( """
                                          {"id":"g-1","status":"open","price":"10","create_time":1700000000}
                                          """ ) ) );

        GateRestClient client = new GateRestClient(
            server.baseUrl(),
            "k",
            "s",
            5000,
            Mockito.mock( GateFeignClient.class )
        );

        TestOrderResult result = client.placeTestOrder(
            new PlaceTestOrderCommand( "gate", "SOL/USDT", OrderSide.BUY, OrderType.MARKET, new BigDecimal( "2" ), null )
        );

        verify( postRequestedFor( urlEqualTo( "/futures/usdt/orders" ) ) );
        assertThat( result.exchangeOrderId() ).isEqualTo( "g-1" );
        assertThat( result.status() ).isEqualTo( "open" );
        assertThat( result.exchangeTsMillis() ).isEqualTo( 1_700_000_000_000L );
    }
}
