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
        venues.forEach( venue -> load( venue, mode ) );
        log.info( "Engine credentials loaded: {}/{} venue(s) ready", cache.size(), venues.size() );
    }

    public void load( String venue, String mode )
    {
        planClient.fetchCredentials( venue, mode ).ifPresentOrElse(
            creds -> {
                cache.put( venue, creds );
                log.info( "Engine credentials loaded for venue={} mode={}", venue, mode );
            },
            () -> log.warn( "Engine credentials not found in monitor for venue={} mode={} — live orders will fail for this venue", venue, mode )
        );
    }

    public Optional<EngineVenueCredentials> get( String venue )
    {
        return Optional.ofNullable( cache.get( venue ) );
    }
}
