package com.crypto.funding.support;

import com.crypto.funding.application.candidate.IngestSignalCandidateCommand;

import java.time.Instant;

public final class ManualSignalTestSupport
{
    private ManualSignalTestSupport()
    {
    }

    public static IngestSignalCandidateCommand manualSignal(
        long sourceChatId,
        long sourceMessageId,
        String venue,
        String symbol,
        Instant detectedAt
    )
    {
        return new IngestSignalCandidateCommand(
            "MANUAL_TEST",
            sourceChatId,
            sourceMessageId,
            "{\"symbol\":\"" + symbol + "\",\"venue\":\"" + venue + "\"}",
            venue,
            symbol,
            detectedAt
        );
    }
}
