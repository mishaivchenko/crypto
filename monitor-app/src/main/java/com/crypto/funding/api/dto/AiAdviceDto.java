package com.crypto.funding.api.dto;

import com.crypto.funding.domain.ai.AiRecommendation;

import java.time.Instant;

public record AiAdviceDto(
    AiRecommendation recommendation,
    double confidence,
    String reasoning,
    String modelUsed,
    Instant analyzedAt
)
{
}
