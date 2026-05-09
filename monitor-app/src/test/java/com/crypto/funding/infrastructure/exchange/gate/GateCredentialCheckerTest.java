package com.crypto.funding.infrastructure.exchange.gate;

import com.crypto.funding.application.port.VenueCredentialCheckPort;
import com.crypto.funding.config.VenueHttpProperties;
import com.crypto.funding.domain.venue.VenueAccessMode;
import com.crypto.funding.domain.venue.VenueConnectionStatus;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

class GateCredentialCheckerTest
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
    void checksFuturesAccountReadiness()
        throws Exception
    {
        server.start();
        server.stubFor( com.github.tomakehurst.wiremock.client.WireMock.get( urlPathEqualTo( "/futures/usdt/accounts" ) )
                                                                      .willReturn( okJson( "{}" ) ) );

        GateCredentialChecker checker = new GateCredentialChecker( HttpClient.newHttpClient(), new VenueHttpProperties() );

        VenueCredentialCheckPort.Result result = checker.check( new VenueCredentialCheckPort.Credentials(
            "gate",
            VenueAccessMode.TESTNET,
            server.baseUrl(),
            "api-key",
            "secret-key",
            null
        ) );

        assertThat( result.status() ).isEqualTo( VenueConnectionStatus.CONNECTED );
        server.verify( getRequestedFor( urlPathEqualTo( "/futures/usdt/accounts" ) ) );
    }
}
