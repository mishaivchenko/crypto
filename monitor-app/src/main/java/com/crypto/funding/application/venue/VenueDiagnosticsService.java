package com.crypto.funding.application.venue;

import com.crypto.funding.application.ResourceNotFoundException;
import com.crypto.funding.application.security.OperatorCredentialService;
import com.crypto.funding.config.MetadataSyncProperties;
import com.crypto.funding.domain.venue.VenueAccessMode;
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
        boolean apiKeyLoaded,
        boolean secretKeyLoaded,
        boolean passphraseLoaded,
        boolean credentialsRequired,
        boolean modeOverridden,
        List<VenueAccessMode> availableModes,
        com.crypto.funding.domain.venue.VenueConnectionStatus connectionStatus,
        String connectionMessage,
        Integer lastConnectionHttpStatus,
        Instant lastCheckedAt,
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
    private final VenueProfileService venueProfileService;
    private final OperatorCredentialService operatorCredentialService;
    private final Environment environment;

    public VenueDiagnosticsService(
        MetadataSyncProperties metadataSyncProperties,
        InstrumentRegistryService instrumentRegistryService,
        VenueRequestTimingService venueRequestTimingService,
        VenueProfileService venueProfileService,
        OperatorCredentialService operatorCredentialService,
        Environment environment
    )
    {
        this.metadataSyncProperties = metadataSyncProperties;
        this.instrumentRegistryService = instrumentRegistryService;
        this.venueRequestTimingService = venueRequestTimingService;
        this.venueProfileService = venueProfileService;
        this.operatorCredentialService = operatorCredentialService;
        this.environment = environment;
    }

    @Transactional(readOnly = true)
    public List<VenueSummary> listVenues()
    {
        Set<String> venues = new LinkedHashSet<>();
        venues.addAll( normalizedEnabledVenues() );
        return venues.stream().sorted().map( this::buildSummary ).toList();
    }

    @Transactional(readOnly = true)
    public VenueProfileService.GlobalAccessProfile getGlobalMode()
    {
        return venueProfileService.getGlobalAccessProfile();
    }

    @Transactional(readOnly = true)
    public VenueSummary getVenue( String rawVenue )
    {
        String venue = normalizeVenue( rawVenue );
        VenueSummary summary = buildSummary( venue );
        if( !summary.enabledForMetadata() )
        {
            throw new ResourceNotFoundException( "Venue not configured: " + rawVenue );
        }
        return summary;
    }

    @Transactional
    public VenueSummary syncVenue( String rawVenue )
    {
        String venue = normalizeVenue( rawVenue );
        ensureEnabledVenue( venue );
        instrumentRegistryService.syncVenue( venue );
        return buildSummary( venue );
    }

    @Transactional
    public VenueSummary setMode( String rawVenue, VenueAccessMode mode )
    {
        String venue = normalizeVenue( rawVenue );
        ensureEnabledVenue( venue );
        venueProfileService.setMode( venue, mode );
        return buildSummary( venue );
    }

    @Transactional
    public VenueProfileService.GlobalAccessProfile setGlobalMode( VenueAccessMode mode )
    {
        return venueProfileService.setGlobalMode( mode );
    }

    @Transactional
    public VenueSummary checkCredentials( String rawVenue )
    {
        String venue = normalizeVenue( rawVenue );
        ensureEnabledVenue( venue );
        if( !venueProfileService.hasChecker( venue ) )
        {
            throw new ResourceNotFoundException( "Проверка ключей не поддерживается для площадки: " + rawVenue );
        }

        long startNanos = System.nanoTime();
        try
        {
            var profile = venueProfileService.getProfile( venue );
            var result = operatorCredentialService.checkMine( venue, profile.mode() );
            venueRequestTimingService.recordSuccess( venue, "credential-check", System.nanoTime() - startNanos, 0L, result.lastConnectionHttpStatus() );
        }
        catch( Exception ex )
        {
            venueRequestTimingService.recordFailure( venue, "credential-check", System.nanoTime() - startNanos, ex.getMessage() );
            if( ex instanceof RuntimeException runtimeException )
            {
                throw runtimeException;
            }
            throw new IllegalStateException( ex );
        }
        return buildSummary( venue );
    }

    private void ensureEnabledVenue( String venue )
    {
        if( !normalizedEnabledVenues().contains( venue ) )
        {
            throw new ResourceNotFoundException( "Venue not configured: " + venue );
        }
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
        VenueProfileService.VenueAccessProfile accessProfile = venueProfileService.getProfile( venue );
        String mode = accessProfile.mode().propertyValue();
        OperatorCredentialService.CredentialSummary credentialSummary =
            operatorCredentialService.currentOperatorSummary( venue, accessProfile.mode() );
        List<InstrumentMetadata> instruments = instrumentRegistryService.listVenueInstruments( venue );
        Instant lastSyncedAt = instruments.stream()
                                          .map( InstrumentMetadata::lastSyncedAt )
                                          .filter( value -> value != null )
                                          .max( Instant::compareTo )
                                          .orElse( null );
        return new VenueSummary(
            venue,
            mode,
            environment.getProperty(
                "trading." + venue + ".metadata-base-url",
                environment.getProperty( "trading." + venue + "." + mode + ".base-url" )
            ),
            environment.getProperty( "trading." + venue + ".contracts-base-url" ),
            credentialSummary.configured(),
            credentialSummary.apiKeyMask() != null,
            credentialSummary.secretKeyMask() != null,
            credentialSummary.passphraseMask() != null,
            credentialsRequired(),
            accessProfile.modeOverridden(),
            accessProfile.availableModes(),
            credentialSummary.connectionStatus(),
            credentialSummary.connectionMessage(),
            credentialSummary.lastConnectionHttpStatus(),
            credentialSummary.lastCheckedAt(),
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

    private boolean credentialsRequired()
    {
        return metadataSyncProperties.isRequireCredentialsOnStartup();
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
