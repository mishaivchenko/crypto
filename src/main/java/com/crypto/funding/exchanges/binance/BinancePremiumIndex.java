package com.crypto.funding.exchanges.binance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties( ignoreUnknown = true )
public class BinancePremiumIndex
{
    public String symbol;
    public String lastFundingRate;
    public long nextFundingTime;
}
