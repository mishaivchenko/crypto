package com.crypto.funding.engine;

import java.time.Instant;
import java.util.Map;

public record EngineSummaryResponse(
    String module,
    String version,
    int totalPlans,
    int actionablePlans,
    Instant generatedAt,
    Map<EnginePlanStatus, Long> statusBreakdown
)
{
}
