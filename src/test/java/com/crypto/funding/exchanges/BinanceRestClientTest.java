package com.crypto.funding.exchanges;

import com.crypto.funding.exchanges.binance.BinanceFeignClient;
import com.crypto.funding.exchanges.binance.BinanceRestClient;
import com.crypto.funding.trading.OrderSide;
import com.crypto.funding.trading.OrderType;
import com.crypto.funding.trading.PlaceTestOrderCommand;
import com.crypto.funding.trading.TestOrderResult;
import com.crypto.funding.watchlist.SymbolRules;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

class BinanceRestClientTest
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
    void placesLimitOrder() throws Exception
    {
        stubFor( post( urlPathEqualTo( "/fapi/v1/order/test" ) )
                     .willReturn( aResponse()
                                      .withStatus( 200 )
                                      .withHeader( "Content-Type", "application/json" )
                                      .withBody( """
                                          {"orderId":"777","status":"NEW","price":"101.00","updateTime":1700000000000}
                                          """ ) ) );

        BinanceRestClient client = new BinanceRestClient(
            server.baseUrl(),
            "k",
            "s",
            5000
        );

        TestOrderResult result = client.placeTestOrder(
            new PlaceTestOrderCommand( "binance", "ETH/USDT", OrderSide.SELL, OrderType.LIMIT, new BigDecimal( "0.5" ), new BigDecimal( "101.00" ) )
        );

        verify( postRequestedFor( urlPathEqualTo( "/fapi/v1/order/test" ) ) );
        assertThat( result.exchangeOrderId() ).isEqualTo( "777" );
        assertThat( result.price() ).isEqualByComparingTo( "101.00" );
        assertThat( result.symbolUnified() ).isEqualTo( "ETH/USDT" );
        assertThat( result.exchangeTsMillis() ).isEqualTo( 1_700_000_000_000L );
    }

    @Test
    void fetchesSymbolRules() throws Exception
    {
        stubFor( get( urlPathEqualTo( "/fapi/v1/exchangeInfo" ) )
                     .withQueryParam( "symbol", equalTo( "BTCUSDT" ) )
                     .willReturn( aResponse()
                                      .withStatus( 200 )
                                      .withHeader( "Content-Type", "application/json" )
                                      .withBody( """
                                          {
                                            "symbols": [
                                              {
                                                "symbol": "BTCUSDT",
                                                "filters": [
                                                  {"filterType":"PRICE_FILTER","minPrice":"0.01","tickSize":"0.01"},
                                                  {"filterType":"LOT_SIZE","minQty":"0.001","stepSize":"0.001"},
                                                  {"filterType":"MIN_NOTIONAL","notional":"5"}
                                                ]
                                              }
                                            ]
                                          }
                                          """ ) ) );

        BinanceRestClient client = new BinanceRestClient(
            server.baseUrl(),
            "k",
            "s",
            5000
        );

        SymbolRules rules = client.fetchRules( "BTC/USDT" );

        assertThat( rules.minOrderQty() ).isEqualByComparingTo( "0.001" );
        assertThat( rules.qtyStep() ).isEqualByComparingTo( "0.001" );
        assertThat( rules.minNotionalValue() ).isEqualByComparingTo( "5" );
    }
}
