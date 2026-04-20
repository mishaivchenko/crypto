package com.crypto.funding.application.candidate;

import java.math.BigDecimal;
import java.time.Instant;

public record IngestSignalCandidateCommand(
    String sourceType,
    Long sourceChatId,
    Long sourceMessageId,
    String rawPayload,
    String sourceVenue,
    String rawSymbol,
    Instant detectedAt,
    Instant sourceFundingTime,
    BigDecimal sourceFundingRatePct
)
{
}
