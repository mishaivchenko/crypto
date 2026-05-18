package com.crypto.funding.application.port;

import com.crypto.funding.domain.liquidity.OrderBookSnapshot;

import java.io.IOException;

public interface VenueOrderBookPort
{
    String venue();

    OrderBookSnapshot fetchOrderBook( String venueSymbol, int depth ) throws IOException, InterruptedException;
}
