package com.crypto.funding.exchanges.gate;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient( name = "gateFeign", url = "${trading.gate.base-url}" )
public interface GateFeignClient
{
    @GetMapping( "/futures/usdt/contracts/{contract}" )
    GateContract getContract( @PathVariable( "contract" ) String contract );
}
