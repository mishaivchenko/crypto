package com.crypto.funding.exchanges;

import com.crypto.funding.exchanges.binance.BinanceFeignClient;
import com.crypto.funding.exchanges.binance.BinanceRestClient;
import com.crypto.funding.trading.OrderSide;
import com.crypto.funding.trading.OrderType;
import com.crypto.funding.trading.PlaceTestOrderCommand;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExchangeClientErrorHandlingTest
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
    void throwsOnHttp500()
    {
        stubFor( post( urlPathEqualTo( "/fapi/v1/order/test" ) )
                     .willReturn( aResponse().withStatus( 500 ).withBody( "boom" ) ) );

        BinanceRestClient client = new BinanceRestClient(
            server.baseUrl(),
            "k",
            "s",
            5000
        );

        assertThatThrownBy( () -> client.placeTestOrder(
            new PlaceTestOrderCommand( "binance", "BTC/USDT", OrderSide.BUY, OrderType.MARKET, new BigDecimal( "1" ), null )
        ) ).hasMessageContaining( "Order failed" );
    }
}
