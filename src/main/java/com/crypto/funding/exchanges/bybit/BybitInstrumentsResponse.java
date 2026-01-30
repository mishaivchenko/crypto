package com.crypto.funding.exchanges.bybit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties( ignoreUnknown = true )
public class BybitInstrumentsResponse
{
    public BybitInstrumentsResult result;
}

@JsonIgnoreProperties( ignoreUnknown = true )
class BybitInstrumentsResult
{
    public BybitInstrument[] list;
}

@JsonIgnoreProperties( ignoreUnknown = true )
class BybitInstrument
{
    public String symbol;
    public BybitLotSizeFilter lotSizeFilter;
}

@JsonIgnoreProperties( ignoreUnknown = true )
class BybitLotSizeFilter
{
    public String minOrderQty;
    public String qtyStep;
    public String minNotionalValue;
}
