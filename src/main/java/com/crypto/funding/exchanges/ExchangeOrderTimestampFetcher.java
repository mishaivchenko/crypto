package com.crypto.funding.exchanges;

public interface ExchangeOrderTimestampFetcher
{
    Long fetchOrderTimestamp(String unifiedSymbol, String exchangeOrderId) throws Exception;
}
