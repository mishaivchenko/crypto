package com.crypto.funding.infrastructure.source;

import com.crypto.funding.config.FundingCandidateSourceProperties;
import com.crypto.funding.config.VenueHttpProperties;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FundingApiPayloadFetcherTest
{
    private final WireMockServer fundingApi = new WireMockServer( options().dynamicPort() );

    @AfterEach
    void stopServer()
    {
        if( fundingApi.isRunning() )
        {
            fundingApi.stop();
        }
    }

    @Test
    void fetchReturnsParsedEntries() throws Exception
    {
        fundingApi.start();
        fundingApi.stubFor( get( urlEqualTo( "/api/funding" ) )
            .willReturn( okJson( """
                {
                  "data": [
                    {
                      "id": 7,
                      "symbol": "BTCUSDT",
                      "coin": "BTC",
                      "exchange": "bybit",
                      "funding": "0.0125",
                      "funding_interval": 8,
                      "updated_at": "2030-04-04 07:30:00"
                    }
                  ]
                }
                """ ) ) );

        FundingApiPayloadFetcher fetcher = new FundingApiPayloadFetcher(
            HttpClient.newHttpClient(),
            httpProperties(),
            sourceProperties( fundingApi.baseUrl() + "/api/funding" )
        );

        FundingApiResponse response = fetcher.fetch();

        assertThat( response.data() ).singleElement().satisfies( entry -> {
            assertThat( entry.id() ).isEqualTo( 7L );
            assertThat( entry.symbol() ).isEqualTo( "BTCUSDT" );
            assertThat( entry.exchange() ).isEqualTo( "bybit" );
        } );
    }

    @Test
    void fetchReturnsEmptyPayloadWhenDataIsMissing() throws Exception
    {
        fundingApi.start();
        fundingApi.stubFor( get( urlEqualTo( "/api/funding" ) )
            .willReturn( okJson( "{}" ) ) );

        FundingApiPayloadFetcher fetcher = new FundingApiPayloadFetcher(
            HttpClient.newHttpClient(),
            httpProperties(),
            sourceProperties( fundingApi.baseUrl() + "/api/funding" )
        );

        FundingApiResponse response = fetcher.fetch();

        assertThat( response.data() ).isEmpty();
    }

    @Test
    void fetchThrowsOnNonSuccessStatus()
    {
        fundingApi.start();
        fundingApi.stubFor( get( urlEqualTo( "/api/funding" ) )
            .willReturn( serverError().withBody( "boom" ) ) );

        FundingApiPayloadFetcher fetcher = new FundingApiPayloadFetcher(
            HttpClient.newHttpClient(),
            httpProperties(),
            sourceProperties( fundingApi.baseUrl() + "/api/funding" )
        );

        assertThatThrownBy( fetcher::fetch )
            .isInstanceOf( IOException.class )
            .hasMessageContaining( "500" )
            .hasMessageContaining( "boom" );
    }

    private static VenueHttpProperties httpProperties()
    {
        VenueHttpProperties properties = new VenueHttpProperties();
        properties.setConnectTimeoutMs( 1_000 );
        properties.setRequestTimeoutMs( 5_000 );
        properties.setPreferHttp2( false );
        return properties;
    }

    private static FundingCandidateSourceProperties sourceProperties( String url )
    {
        FundingCandidateSourceProperties properties = new FundingCandidateSourceProperties();
        properties.setUrl( url );
        properties.setSourceType( "FUNDING_API" );
        return properties;
    }
}
