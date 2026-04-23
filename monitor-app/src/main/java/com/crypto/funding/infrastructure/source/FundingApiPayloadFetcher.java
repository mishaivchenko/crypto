package com.crypto.funding.infrastructure.source;

import com.crypto.funding.config.FundingCandidateSourceProperties;
import com.crypto.funding.config.VenueHttpProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
class FundingApiPayloadFetcher
{
    private final HttpClient httpClient;
    private final VenueHttpProperties venueHttpProperties;
    private final FundingCandidateSourceProperties sourceProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    FundingApiPayloadFetcher(
        HttpClient httpClient,
        VenueHttpProperties venueHttpProperties,
        FundingCandidateSourceProperties sourceProperties
    )
    {
        this.httpClient = httpClient;
        this.venueHttpProperties = venueHttpProperties;
        this.sourceProperties = sourceProperties;
    }

    FundingApiResponse fetch() throws IOException, InterruptedException
    {
        HttpRequest request = HttpRequest.newBuilder()
                                         .uri( URI.create( sourceProperties.getUrl() ) )
                                         .timeout( Duration.ofMillis( venueHttpProperties.getRequestTimeoutMs() ) )
                                         .GET()
                                         .build();

        HttpResponse<String> response = httpClient.send( request, HttpResponse.BodyHandlers.ofString() );
        if( response.statusCode() >= 300 )
        {
            throw new IOException( "Funding API request failed: " + response.statusCode() + " body=" + response.body() );
        }

        FundingApiResponse payload = objectMapper.readValue( response.body(), FundingApiResponse.class );
        return payload == null ? new FundingApiResponse( java.util.List.of() ) : payload;
    }
}
