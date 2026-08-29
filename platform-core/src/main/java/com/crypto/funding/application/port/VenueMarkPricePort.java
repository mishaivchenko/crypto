package com.crypto.funding.application.port;

import java.io.IOException;
import java.math.BigDecimal;

public interface VenueMarkPricePort {
    String venue();

    BigDecimal getMarkPrice(String venueSymbol) throws IOException, InterruptedException;
}
