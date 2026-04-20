package com.crypto.funding.application.candidate;

import java.math.BigDecimal;
import java.time.Instant;

public record ApproveSignalCandidateCommand(
    Long candidateId,
    String venue,
    String symbol,
    Instant fundingTime,
    BigDecimal fundingRatePct,
    String reviewNotes
)
{
}
