package com.crypto.funding.infrastructure.source;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.LinkedHashSet;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record FundingApiResponse( List<FundingApiEntry> data )
{
    FundingApiResponse
    {
        data = data == null ? List.of() : List.copyOf( new LinkedHashSet<>( data ) );
    }
}
