package com.crypto.funding.application.venue;

import com.crypto.funding.application.ResourceNotFoundException;
import com.crypto.funding.application.port.VenueCredentialCheckPort;
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
    private final Environment environment;

    public VenueDiagnosticsService(
        MetadataSyncProperties metadataSyncProperties,
        InstrumentRegistryService instrumentRegistryService,
        VenueRequestTimingService venueRequestTimingService,
        VenueProfileService venueProfileService,
        Environment environment
    )
    {
        this.metadataSyncProperties = metadataSyncProperties;
        this.instrumentRegistryService = instrumentRegistryService;
        this.venueRequestTimingService = venueRequestTimingService;
        this.venueProfileService = venueProfileService;
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

        VenueProfileService.ResolvedCredentials credentials = venueProfileService.resolveCredentials( venue );
        if( !credentials.credentialsLoaded() )
        {
            venueProfileService.saveCheckResult(
                venue,
                new VenueCredentialCheckPort.Result(
                    com.crypto.funding.domain.venue.VenueConnectionStatus.NOT_CONNECTED,
                    "Ключи не подключены.",
                    null
                )
            );
            return buildSummary( venue );
        }

        VenueCredentialCheckPort.Result result;
        try
        {
            result = venueProfileService.checker( venue ).check( new VenueCredentialCheckPort.Credentials(
                credentials.venue(),
                credentials.mode(),
                credentials.baseUrl(),
                credentials.apiKey(),
                credentials.secretKey(),
                credentials.passphrase()
            ) );
        }
        catch( Exception ex )
        {
            result = new VenueCredentialCheckPort.Result(
                com.crypto.funding.domain.venue.VenueConnectionStatus.ERROR,
                ex.getMessage() == null || ex.getMessage().isBlank() ? "Ошибка проверки ключей." : ex.getMessage(),
                null
            );
        }

        venueProfileService.saveCheckResult( venue, result );
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
            accessProfile.credentialsLoaded(),
            accessProfile.apiKeyLoaded(),
            accessProfile.secretKeyLoaded(),
            accessProfile.passphraseLoaded(),
            credentialsRequired(),
            accessProfile.modeOverridden(),
            accessProfile.availableModes(),
            accessProfile.connectionStatus(),
            accessProfile.connectionMessage(),
            accessProfile.lastConnectionHttpStatus(),
            accessProfile.lastCheckedAt(),
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
        if( metadataSyncProperties.isRequireCredentialsOnStartup() )
        {
            return true;
        }
        String executionMode = environment.getProperty( "trading.execution.mode", "DISABLED" );
        return executionMode != null && "LIVE".equalsIgnoreCase( executionMode.trim() );
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
