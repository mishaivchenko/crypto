package com.crypto.funding.market;

import com.crypto.funding.market.model.Quote;

import java.util.Map;

public interface MarketCache
{
    void put(String exchange, String unifiedSymbol, Quote q);
    Quote get(String exchange, String unifiedSymbol);
    Map<String, Quote> getAll(String unifiedSymbol);
    boolean isFresh(String exchange, String unifiedSymbol, long maxAgeMillis);
}
