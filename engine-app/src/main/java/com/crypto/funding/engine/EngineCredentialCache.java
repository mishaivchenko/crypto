package com.crypto.funding.engine;

import com.crypto.funding.contract.engine.EngineVenueCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EngineCredentialCache
{
    private static final Logger log = LoggerFactory.getLogger( EngineCredentialCache.class );
    private static final int MAX_RETRIES = 10;
    private static final long RETRY_INTERVAL_MS = 10_000L;

    private final Map<String, EngineVenueCredentials> cache = new ConcurrentHashMap<>();
    private final EnginePlanClient planClient;
    private final EngineProperties properties;

    public EngineCredentialCache( EnginePlanClient planClient, EngineProperties properties )
    {
        this.planClient = planClient;
        this.properties = properties;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStartup()
    {
        String mode = properties.getTradingVenueAccessMode();
        List<String> venues = properties.liveEnabledVenues();
        log.info( "Loading engine credentials for {} venue(s) in background", venues.size() );

        for( int attempt = 1; attempt <= MAX_RETRIES; attempt++ )
        {
            venues.stream()
                  .filter( venue -> !cache.containsKey( venue ) )
                  .forEach( venue -> load( venue, mode ) );

            int loaded = cache.size();
            log.info( "Engine credentials loaded: {}/{} venue(s) ready (attempt {}/{})", loaded, venues.size(), attempt, MAX_RETRIES );

            if( loaded >= venues.size() )
            {
                return;
            }
            if( attempt < MAX_RETRIES )
            {
                try
                {
                    Thread.sleep( retryIntervalMs() );
                }
                catch( InterruptedException e )
                {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        log.warn( "Engine credentials still incomplete after {} retries: {}/{} venue(s) ready", MAX_RETRIES, cache.size(), venues.size() );
    }

    public void load( String venue, String mode )
    {
        planClient.fetchCredentials( venue, mode ).ifPresentOrElse(
            creds -> {
                cache.put( venue, creds );
                log.info( "Engine credentials loaded for venue={} mode={}", venue, mode );
            },
            () -> log.warn( "Engine credentials not found in monitor for venue={} mode={} — will retry", venue, mode )
        );
    }

    public Optional<EngineVenueCredentials> get( String venue )
    {
        return Optional.ofNullable( cache.get( venue ) );
    }

    protected long retryIntervalMs()
    {
        return RETRY_INTERVAL_MS;
    }
}
