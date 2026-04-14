package com.crypto.funding.domain.venue;

public enum VenueAccessMode
{
    TESTNET,
    PRODUCTION;

    public String propertyValue()
    {
        return name().toLowerCase();
    }
}
