package com.crypto.funding.infrastructure.exchange.gate;

import com.crypto.funding.application.venue.VenueProfileService;
import com.crypto.funding.config.VenueHttpProperties;
import com.crypto.funding.domain.venue.VenueAccessMode;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

class GateMetadataAdapterTest
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
    void derivesContractStepAndMinimumNotional()
        throws Exception
    {
        server.start();
        server.stubFor( get( urlPathEqualTo( "/futures/usdt/contracts" ) )
            .willReturn( okJson( """
                [
                  {
                    "name": "BTC_USDT",
                    "in_delisting": false,
                    "order_size_min": 1,
                    "enable_decimal": false,
                    "quanto_multiplier": "0.0001",
                    "last_price": "80260.9"
                  }
                ]
                """ ) ) );

        GateMetadataAdapter adapter = new GateMetadataAdapter(
            HttpClient.newHttpClient(),
            new VenueHttpProperties(),
            server.baseUrl(),
            venueProfileService()
        );

        var instruments = adapter.fetchPerpetualInstruments();

        assertThat( instruments ).singleElement().satisfies( instrument -> {
            assertThat( instrument.canonicalSymbol() ).isEqualTo( "BTC/USDT" );
            assertThat( instrument.venueSymbol() ).isEqualTo( "BTC_USDT" );
            assertThat( instrument.minOrderQty() ).isEqualByComparingTo( "1" );
            assertThat( instrument.qtyStep() ).isEqualByComparingTo( "1" );
            assertThat( instrument.minNotionalValue() ).isEqualByComparingTo( "8.02609" );
        } );
    }

    private VenueProfileService venueProfileService()
    {
        return new VenueProfileService( List.of(), null, null )
        {
            @Override
            public ResolvedCredentials resolveCredentials( String rawVenue )
            {
                return new ResolvedCredentials(
                    "gate",
                    VenueAccessMode.TESTNET,
                    server.baseUrl(),
                    "api-key",
                    "secret-key",
                    null
                );
            }
        };
    }
}
