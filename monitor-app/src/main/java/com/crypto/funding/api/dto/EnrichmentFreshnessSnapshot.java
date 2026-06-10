package com.crypto.funding.api.dto;

public record EnrichmentFreshnessSnapshot(
    Double avgSecondsSinceLastAssessment,
    long uncoveredEntityCount
)
{
}
