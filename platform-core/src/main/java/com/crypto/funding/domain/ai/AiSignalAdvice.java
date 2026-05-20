package com.crypto.funding.domain.ai;

import java.time.Instant;

public record AiSignalAdvice(
    Long id,
    Long signalCandidateId,
    AiRecommendation recommendation,
    double confidence,
    String reasoning,
    String modelUsed,
    Integer promptTokens,
    Integer completionTokens,
    Instant analyzedAt,
    Instant createdAt
)
{
    public AiSignalAdvice
    {
        if( signalCandidateId == null )
        {
            throw new IllegalArgumentException( "signalCandidateId must not be null" );
        }
        if( recommendation == null )
        {
            throw new IllegalArgumentException( "recommendation must not be null" );
        }
        if( analyzedAt == null )
        {
            throw new IllegalArgumentException( "analyzedAt must not be null" );
        }
    }
}
