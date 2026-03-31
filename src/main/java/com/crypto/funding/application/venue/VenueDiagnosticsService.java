package com.crypto.funding.application.venue;

import com.crypto.funding.application.ResourceNotFoundException;
import com.crypto.funding.config.MetadataSyncProperties;
import com.crypto.funding.domain.venue.InstrumentMetadata;
import com.crypto.funding.domain.venue.InstrumentStatus;
import com.crypto.funding.infrastructure.telemetry.VenueRequestTimingService;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class VenueDiagnosticsService
{
    public record VenueSummary(
        String venue,
        String configuredMode,
        String metadataBaseUrl,
        String contractsBaseUrl,
        boolean credentialsConfigured,
        boolean enabledForMetadata,
        boolean metadataProviderAvailable,
        long activeInstrumentCount,
        Instant lastSyncedAt
    )
    {
    }

    private final MetadataSyncProperties metadataSyncProperties;
    private final InstrumentRegistryService instrumentRegistryService;
    private final VenueRequestTimingService venueRequestTimingService;
    private final Environment environment;

    public VenueDiagnosticsService(
        MetadataSyncProperties metadataSyncProperties,
        InstrumentRegistryService instrumentRegistryService,
        VenueRequestTimingService venueRequestTimingService,
        Environment environment
    )
    {
        this.metadataSyncProperties = metadataSyncProperties;
        this.instrumentRegistryService = instrumentRegistryService;
        this.venueRequestTimingService = venueRequestTimingService;
        this.environment = environment;
    }

    @Transactional(readOnly = true)
    public List<VenueSummary> listVenues()
    {
        Set<String> venues = new LinkedHashSet<>();
        venues.addAll( normalizedEnabledVenues() );
        venues.addAll( instrumentRegistryService.supportedVenues() );
        return venues.stream().sorted().map( this::buildSummary ).toList();
    }

    @Transactional(readOnly = true)
    public VenueSummary getVenue( String rawVenue )
    {
        String venue = normalizeVenue( rawVenue );
        VenueSummary summary = buildSummary( venue );
        if( !summary.enabledForMetadata() && !summary.metadataProviderAvailable() )
        {
            throw new ResourceNotFoundException( "Venue not configured: " + rawVenue );
        }
        return summary;
    }

    @Transactional
    public VenueSummary syncVenue( String rawVenue )
    {
        String venue = normalizeVenue( rawVenue );
        instrumentRegistryService.syncVenue( venue );
        return buildSummary( venue );
    }

    @Transactional(readOnly = true)
    public List<InstrumentMetadata> listInstruments( String rawVenue, boolean activeOnly )
    {
        return instrumentRegistryService.listVenueInstruments( normalizeVenue( rawVenue ) )
                                        .stream()
                                        .filter( instrument -> !activeOnly || instrument.status() == InstrumentStatus.ACTIVE )
                                        .toList();
    }

    @Transactional(readOnly = true)
    public List<VenueRequestTimingService.Snapshot> listTimings()
    {
        return venueRequestTimingService.snapshots();
    }

    @Transactional(readOnly = true)
    public List<VenueRequestTimingService.Snapshot> listTimings( String rawVenue )
    {
        return venueRequestTimingService.snapshots( normalizeVenue( rawVenue ) );
    }

    private VenueSummary buildSummary( String venue )
    {
        String mode = configuredMode( venue );
        List<InstrumentMetadata> instruments = instrumentRegistryService.listVenueInstruments( venue );
        Instant lastSyncedAt = instruments.stream()
                                          .map( InstrumentMetadata::lastSyncedAt )
                                          .filter( value -> value != null )
                                          .max( Instant::compareTo )
                                          .orElse( null );
        return new VenueSummary(
            venue,
            mode,
            environment.getProperty( "trading." + venue + "." + mode + ".base-url" ),
            environment.getProperty( "trading." + venue + ".contracts-base-url" ),
            credentialsConfigured( venue, mode ),
            normalizedEnabledVenues().contains( venue ),
            instrumentRegistryService.hasProvider( venue ),
            instrumentRegistryService.countActiveInstruments( venue ),
            lastSyncedAt
        );
    }

    private List<String> normalizedEnabledVenues()
    {
        return metadataSyncProperties.getEnabledVenues().stream().map( this::normalizeVenue ).distinct().toList();
    }

    private String configuredMode( String venue )
    {
        return normalizeMode( environment.getProperty( "trading." + venue + ".mode", "production" ) );
    }

    private boolean credentialsConfigured( String venue, String mode )
    {
        return hasText( environment.getProperty( "trading." + venue + "." + mode + ".api-key" ) )
               && hasText( environment.getProperty( "trading." + venue + "." + mode + ".secret-key" ) );
    }

    private static boolean hasText( String value )
    {
        return value != null && !value.isBlank();
    }

    private String normalizeMode( String rawMode )
    {
        if( rawMode == null || rawMode.isBlank() )
        {
            return "production";
        }
        return rawMode.trim().toLowerCase( Locale.ROOT );
    }

    private String normalizeVenue( String rawVenue )
    {
        if( rawVenue == null || rawVenue.isBlank() )
        {
            throw new IllegalArgumentException( "venue must not be blank" );
        }
        return rawVenue.trim().toLowerCase( Locale.ROOT );
    }
}
