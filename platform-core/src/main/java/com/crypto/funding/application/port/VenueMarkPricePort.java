package com.crypto.funding.application.port;

import java.math.BigDecimal;
import java.io.IOException;

public interface VenueMarkPricePort
{
    String venue();

    BigDecimal getMarkPrice( String venueSymbol ) throws IOException, InterruptedException;
}
