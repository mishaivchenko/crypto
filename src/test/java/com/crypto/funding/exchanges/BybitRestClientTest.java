package com.crypto.funding.exchanges;

import com.crypto.funding.exchanges.bybit.BybitFeignClient;
import com.crypto.funding.exchanges.bybit.BybitRestClient;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BybitRestClientTest
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
        stubFor( post( urlEqualTo( "/v5/order/create" ) )
                     .willReturn( aResponse()
                                      .withStatus( 200 )
                                      .withHeader( "Content-Type", "application/json" )
                                      .withBody( """
                                          {"retCode":0,"retMsg":"OK","result":{"orderId":"oid-1","orderStatus":"Filled","avgPrice":"100.5","createdTime":"1700000000001"}}
                                          """ ) ) );

        BybitRestClient client = new BybitRestClient(
            server.baseUrl(),
            "key",
            "secret",
            5000,
            Mockito.mock( BybitFeignClient.class )
        );

        TestOrderResult result = client.placeTestOrder(
            new PlaceTestOrderCommand( "bybit", "BTC/USDT", OrderSide.BUY, OrderType.MARKET, new BigDecimal( "0.01" ), null )
        );

        assertThat( result.exchangeOrderId() ).isEqualTo( "oid-1" );
        assertThat( result.price() ).isEqualByComparingTo( "100.5" );
        assertThat( result.status() ).isEqualTo( "Filled" );
        assertThat( result.exchangeTsMillis() ).isEqualTo( 1_700_000_000_001L );
    }

    @Test
    void failsOnRetCodeNotZero()
    {
        stubFor( post( urlEqualTo( "/v5/order/create" ) )
                     .willReturn( aResponse()
                                      .withStatus( 200 )
                                      .withHeader( "Content-Type", "application/json" )
                                      .withBody( """
                                          {"retCode":100500,"retMsg":"Balance low","result":{}}
                                          """ ) ) );

        BybitRestClient client = new BybitRestClient(
            server.baseUrl(),
            "key",
            "secret",
            5000,
            Mockito.mock( BybitFeignClient.class )
        );

        assertThatThrownBy( () -> client.placeTestOrder(
            new PlaceTestOrderCommand( "bybit", "BTC/USDT", OrderSide.SELL, OrderType.MARKET, new BigDecimal( "1" ), null )
        ) ).hasMessageContaining( "retCode=100500" );
    }
}
