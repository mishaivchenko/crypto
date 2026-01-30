package com.crypto.funding.exchanges.bybit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties( ignoreUnknown = true )
public class BybitMarketTickersResponse
{
    public BybitMarketTickersResult result;
}

@JsonIgnoreProperties( ignoreUnknown = true )
class BybitMarketTickersResult
{
    public BybitTicker[] list;
}

@JsonIgnoreProperties( ignoreUnknown = true )
class BybitTicker
{
    public String symbol;
    public String fundingRate;
    public String indexPrice;
    public String nextFundingTime;
    public String fundingIntervalHour;
}
