package com.crypto.funding.infrastructure.exchange.okx;

import com.crypto.funding.application.venue.VenueProfileService;
import com.crypto.funding.config.VenueHttpProperties;
import com.crypto.funding.domain.venue.InstrumentMetadata;
import com.crypto.funding.domain.venue.VenueAccessMode;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import java.net.http.HttpClient;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OkxMetadataAdapterTest
{
    private final WireMockServer server = new WireMockServer( options().dynamicPort() );

    @AfterEach
    void stop()
    {
        if( server.isRunning() )
        {
            server.stop();
        }
    }

    @Test
    void parsesLinearUsdtSwapsUsingCtValCcyAndSettleCcy()
        throws Exception
    {
        server.start();
        server.stubFor( get( urlPathEqualTo( "/api/v5/public/instruments" ) )
            .willReturn( okJson( """
                {
                  "code": "0",
                  "data": [
                    {
                      "instId": "BTC-USDT-SWAP",
                      "instType": "SWAP",
                      "ctType": "linear",
                      "ctValCcy": "BTC",
                      "settleCcy": "USDT",
                      "baseCcy": "",
                      "quoteCcy": "",
                      "minSz": "0.01",
                      "lotSz": "0.01",
                      "state": "live"
                    },
                    {
                      "instId": "ETH-USDT-SWAP",
                      "instType": "SWAP",
                      "ctType": "linear",
                      "ctValCcy": "ETH",
                      "settleCcy": "USDT",
                      "baseCcy": "",
                      "quoteCcy": "",
                      "minSz": "0.1",
                      "lotSz": "0.1",
                      "state": "live"
                    }
                  ]
                }
                """ ) ) );

        OkxMetadataAdapter adapter = new OkxMetadataAdapter(
            HttpClient.newHttpClient(),
            new VenueHttpProperties(),
            environment( server.baseUrl() ),
            venueProfileService( server.baseUrl() )
        );

        List<InstrumentMetadata> instruments = adapter.fetchPerpetualInstruments();

        assertThat( instruments ).hasSize( 2 );
        assertThat( instruments.get( 0 ).venueSymbol() ).isEqualTo( "BTC-USDT-SWAP" );
        assertThat( instruments.get( 0 ).canonicalSymbol() ).isEqualTo( "BTC/USDT" );
        assertThat( instruments.get( 1 ).venueSymbol() ).isEqualTo( "ETH-USDT-SWAP" );
    }

    @Test
    void skipsInverseCoinMargined()
        throws Exception
    {
        server.start();
        server.stubFor( get( urlPathEqualTo( "/api/v5/public/instruments" ) )
            .willReturn( okJson( """
                {
                  "code": "0",
                  "data": [
                    {
                      "instId": "BTC-USD-SWAP",
                      "instType": "SWAP",
                      "ctType": "inverse",
                      "ctValCcy": "BTC",
                      "settleCcy": "BTC",
                      "baseCcy": "",
                      "quoteCcy": "",
                      "minSz": "1",
                      "lotSz": "1",
                      "state": "live"
                    },
                    {
                      "instId": "SOL-USDT-SWAP",
                      "instType": "SWAP",
                      "ctType": "linear",
                      "ctValCcy": "SOL",
                      "settleCcy": "USDT",
                      "baseCcy": "",
                      "quoteCcy": "",
                      "minSz": "1",
                      "lotSz": "1",
                      "state": "live"
                    }
                  ]
                }
                """ ) ) );

        OkxMetadataAdapter adapter = new OkxMetadataAdapter(
            HttpClient.newHttpClient(),
            new VenueHttpProperties(),
            environment( server.baseUrl() ),
            venueProfileService( server.baseUrl() )
        );

        List<InstrumentMetadata> instruments = adapter.fetchPerpetualInstruments();

        assertThat( instruments ).singleElement().satisfies( i -> {
            assertThat( i.venueSymbol() ).isEqualTo( "SOL-USDT-SWAP" );
            assertThat( i.canonicalSymbol() ).isEqualTo( "SOL/USDT" );
        } );
    }

    @Test
    void skipsNonUsdtSettled()
        throws Exception
    {
        server.start();
        server.stubFor( get( urlPathEqualTo( "/api/v5/public/instruments" ) )
            .willReturn( okJson( """
                {
                  "code": "0",
                  "data": [
                    {
                      "instId": "BTC-USDC-SWAP",
                      "ctType": "linear",
                      "ctValCcy": "BTC",
                      "settleCcy": "USDC",
                      "state": "live"
                    },
                    {
                      "instId": "ETH-USDT-SWAP",
                      "ctType": "linear",
                      "ctValCcy": "ETH",
                      "settleCcy": "USDT",
                      "minSz": "0.1",
                      "lotSz": "0.1",
                      "state": "live"
                    }
                  ]
                }
                """ ) ) );

        OkxMetadataAdapter adapter = new OkxMetadataAdapter(
            HttpClient.newHttpClient(),
            new VenueHttpProperties(),
            environment( server.baseUrl() ),
            venueProfileService( server.baseUrl() )
        );

        List<InstrumentMetadata> instruments = adapter.fetchPerpetualInstruments();

        assertThat( instruments ).singleElement()
            .extracting( InstrumentMetadata::venueSymbol )
            .isEqualTo( "ETH-USDT-SWAP" );
    }

    private Environment environment( String baseUrl )
    {
        Environment env = mock( Environment.class );
        when( env.getProperty( eq( "trading.okx.metadata-base-url" ), any( String.class ) ) ).thenReturn( baseUrl );
        return env;
    }

    private VenueProfileService venueProfileService( String baseUrl )
    {
        VenueProfileService mock = mock( VenueProfileService.class );
        when( mock.resolveCredentials( "okx" ) ).thenReturn(
            new VenueProfileService.ResolvedCredentials( "okx", VenueAccessMode.PRODUCTION, baseUrl, "", "", null )
        );
        return mock;
    }
}
