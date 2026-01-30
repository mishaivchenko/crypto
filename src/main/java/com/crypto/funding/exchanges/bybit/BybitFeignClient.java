package com.crypto.funding.exchanges.bybit;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient( name = "bybitFeign", url = "${trading.bybit.base-url:https://api-testnet.bybit.com}" )
public interface BybitFeignClient
{
    @GetMapping( "/v5/market/tickers" )
    BybitMarketTickersResponse getTickers(
        @RequestParam( "category" ) String category,
        @RequestParam( "symbol" ) String symbol
    );

    @GetMapping( "/v5/market/instruments-info" )
    BybitInstrumentsResponse getInstruments(
        @RequestParam( "category" ) String category,
        @RequestParam( "symbol" ) String symbol
    );
}
