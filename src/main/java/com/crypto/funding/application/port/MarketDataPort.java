package com.crypto.funding.application.port;

import java.math.BigDecimal;
import java.util.Optional;

public interface MarketDataPort
{
    Optional<BigDecimal> fetchReferencePrice( String venue, String symbol );
}
