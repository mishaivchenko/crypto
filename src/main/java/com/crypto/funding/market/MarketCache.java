package com.crypto.funding.market;

import com.crypto.funding.market.model.Quote;
import com.crypto.funding.market.model.MarketSnapshot;

import java.util.Map;
import java.util.Optional;

public interface MarketCache
{
    void put(String exchange, String unifiedSymbol, Quote q);
    Quote get(String exchange, String unifiedSymbol);
    Map<String, Quote> getAll(String unifiedSymbol);
    boolean isFresh(String exchange, String unifiedSymbol, long maxAgeMillis);

    default Optional<MarketSnapshot> getSnapshot(String unifiedSymbol)
    {
        return Optional.empty();
    }
}
