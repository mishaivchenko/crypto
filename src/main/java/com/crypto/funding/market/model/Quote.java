package com.crypto.funding.market.model;

public record Quote(double bid, double ask, long tsNanos)
{
    public boolean valid() { return bid > 0 && ask > 0; }
}
