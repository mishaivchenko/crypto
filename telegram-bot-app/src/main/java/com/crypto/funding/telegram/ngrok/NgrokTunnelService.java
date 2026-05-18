package com.crypto.funding.telegram.ngrok;

import com.crypto.funding.telegram.config.NgrokProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Reads active tunnel URLs from the local ngrok agent API (default: localhost:4040).
 * Used to auto-discover the public staging URL without hardcoding it.
 */
@Service
public class NgrokTunnelService
{
    private static final Logger log = LoggerFactory.getLogger( NgrokTunnelService.class );

    private final NgrokProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public NgrokTunnelService( NgrokProperties properties, ObjectMapper objectMapper )
    {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout( Duration.ofSeconds( 2 ) )
            .build();
    }

    public Optional<NgrokTunnels> fetchTunnels()
    {
        if( !properties.enabled() )
        {
            return Optional.empty();
        }
        try
        {
            HttpRequest request = HttpRequest.newBuilder()
                .uri( URI.create( properties.apiUrl() + "/api/tunnels" ) )
                .timeout( Duration.ofSeconds( 3 ) )
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send( request, HttpResponse.BodyHandlers.ofString() );
            if( response.statusCode() != 200 )
            {
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree( response.body() );
            JsonNode tunnels = root.get( "tunnels" );
            if( tunnels == null || !tunnels.isArray() )
            {
                return Optional.empty();
            }

            String monitorPublicUrl = null;
            String grafanaPublicUrl = null;
            String enginePublicUrl = null;

            for( JsonNode tunnel : tunnels )
            {
                String publicUrl = tunnel.path( "public_url" ).asText( "" );
                String localAddr = tunnel.path( "config" ).path( "addr" ).asText( "" );

                if( !publicUrl.startsWith( "https" ) )
                {
                    continue;
                }

                if( localAddr.contains( ":8090" ) || localAddr.contains( "8090" ) )
                {
                    monitorPublicUrl = publicUrl;
                }
                else if( localAddr.contains( ":3000" ) || localAddr.contains( "3000" ) )
                {
                    grafanaPublicUrl = publicUrl;
                }
                else if( localAddr.contains( ":8091" ) || localAddr.contains( "8091" ) )
                {
                    enginePublicUrl = publicUrl;
                }
            }

            if( monitorPublicUrl == null && grafanaPublicUrl == null && enginePublicUrl == null )
            {
                return Optional.empty();
            }

            log.debug( "Discovered ngrok tunnels — monitor={}, grafana={}, engine={}",
                monitorPublicUrl, grafanaPublicUrl, enginePublicUrl );

            return Optional.of( new NgrokTunnels( monitorPublicUrl, grafanaPublicUrl, enginePublicUrl ) );
        }
        catch( Exception e )
        {
            log.debug( "Could not reach ngrok API at {}: {}", properties.apiUrl(), e.getMessage() );
            return Optional.empty();
        }
    }

    public record NgrokTunnels(
        String monitorUrl,
        String grafanaUrl,
        String engineUrl
    )
    {
    }
}
