package com.crypto.funding.exchanges;

import com.crypto.funding.watchlist.FundingInfo;

public interface ExchangeRestClient
{
    String name(); // "binance", "bybit", "gate"
    FundingInfo fetchFunding(String unifiedSymbol) throws Exception;

}
