package com.crypto.funding.application.candidate;

public record RejectSignalCandidateCommand(
    Long candidateId,
    String reviewNotes
)
{
}
