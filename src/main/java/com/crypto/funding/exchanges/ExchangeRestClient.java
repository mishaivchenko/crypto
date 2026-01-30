package com.crypto.funding.exchanges;

import com.crypto.funding.watchlist.FundingInfo;
import com.crypto.funding.watchlist.SymbolRules;

public interface ExchangeRestClient
{
    String name(); // "binance", "bybit", "gate"
    FundingInfo fetchFunding(String unifiedSymbol) throws Exception;
    SymbolRules fetchRules(String unifiedSymbol);

}
