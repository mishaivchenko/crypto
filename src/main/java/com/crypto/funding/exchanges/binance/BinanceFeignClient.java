package com.crypto.funding.exchanges.binance;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient( name = "binanceFeign", url = "${trading.binance.base-url:https://testnet.binancefuture.com}" )
public interface BinanceFeignClient
{
    @GetMapping( "/fapi/v1/premiumIndex" )
    BinancePremiumIndex getPremiumIndex( @RequestParam( "symbol" ) String symbol );
}
