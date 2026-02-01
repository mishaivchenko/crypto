package com.crypto.funding.exchanges.gate;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Public (unauthenticated) REST client for Gate contract metadata and funding.
 * Default URL now points to the production futures API because testnet
 * intermittently returns 502 for these public endpoints.
 */
@FeignClient(
    name = "gateFeign",
    url = "${trading.gate.contracts-base-url:${GATE_CONTRACTS_BASE_URL:https://fx-api.gateio.ws/api/v4}}"
)
public interface GateFeignClient
{
    @GetMapping( "/futures/usdt/contracts/{contract}" )
    GateContract getContract( @PathVariable( "contract" ) String contract );
}
