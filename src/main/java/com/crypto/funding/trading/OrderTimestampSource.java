package com.crypto.funding.trading;

public enum OrderTimestampSource
{
    RESPONSE_BODY,    // timestamp came directly in initial order response
    FOLLOW_UP_QUERY,  // timestamp was fetched via additional GET order-status call
    UNKNOWN
}
