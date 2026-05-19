package com.crypto.funding.telegram.ngrok;

import com.crypto.funding.telegram.config.NgrokProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Reads active tunnel URLs from the local ngrok agent API.
 * Uses OkHttpClient so we can set a custom Host header — Java HttpClient
 * silently ignores Host overrides (restricted header), which causes ngrok's
 * DNS-rebinding protection to reject requests arriving with an IP Host header.
 */
@Service
public class NgrokTunnelService
{
    private static final Logger log = LoggerFactory.getLogger( NgrokTunnelService.class );

    private final NgrokProperties properties;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;

    public NgrokTunnelService( NgrokProperties properties, ObjectMapper objectMapper )
    {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout( 2, TimeUnit.SECONDS )
            .readTimeout( 3, TimeUnit.SECONDS )
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
            Request request = new Request.Builder()
                .url( properties.apiUrl() + "/api/tunnels" )
                .header( "Host", "localhost" )
                .get()
                .build();

            try( Response response = httpClient.newCall( request ).execute() )
            {
                if( !response.isSuccessful() || response.body() == null )
                {
                    return Optional.empty();
                }

                JsonNode root = objectMapper.readTree( response.body().string() );
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

                    if( localAddr.contains( "8090" ) )
                    {
                        monitorPublicUrl = publicUrl;
                    }
                    else if( localAddr.contains( "3000" ) )
                    {
                        grafanaPublicUrl = publicUrl;
                    }
                    else if( localAddr.contains( "8091" ) )
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
