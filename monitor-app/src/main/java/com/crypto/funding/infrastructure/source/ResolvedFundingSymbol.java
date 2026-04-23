package com.crypto.funding.infrastructure.source;

record ResolvedFundingSymbol(
    String candidateRawSymbol,
    String canonicalSymbol
)
{
}
