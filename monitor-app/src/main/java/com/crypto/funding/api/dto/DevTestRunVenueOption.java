package com.crypto.funding.api.dto;

import java.util.List;

public record DevTestRunVenueOption(
    String venue,
    boolean supported,
    List<DevTestRunSymbolOption> symbols
)
{
}
