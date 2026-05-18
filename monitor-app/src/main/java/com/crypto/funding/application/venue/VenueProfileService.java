package com.crypto.funding.application.venue;

import com.crypto.funding.application.ResourceNotFoundException;
import com.crypto.funding.application.port.VenueCredentialCheckPort;
import com.crypto.funding.domain.venue.VenueAccessMode;
import com.crypto.funding.domain.venue.VenueConnectionStatus;
import com.crypto.funding.infrastructure.persistence.model.VenueProfileEntity;
import com.crypto.funding.infrastructure.persistence.repository.VenueProfileJpaRepository;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class VenueProfileService
{
    private static final String GLOBAL_PROFILE_KEY = "__global__";

    public record GlobalAccessProfile(
        VenueAccessMode mode,
        boolean modeOverridden,
        List<VenueAccessMode> availableModes
    )
    {
    }

    public record VenueAccessProfile(
        String venue,
        VenueAccessMode mode,
        boolean modeOverridden,
        List<VenueAccessMode> availableModes,
        boolean apiKeyLoaded,
        boolean secretKeyLoaded,
        boolean passphraseLoaded,
        VenueConnectionStatus connectionStatus,
        String connectionMessage,
        Integer lastConnectionHttpStatus,
        Instant lastCheckedAt,
        Long defaultManualLatencyAdjustmentMs
    )
    {
        public boolean credentialsLoaded()
        {
            return apiKeyLoaded && secretKeyLoaded && ( !requiresPassphrase( venue ) || passphraseLoaded );
        }
    }

    public record ResolvedCredentials(
        String venue,
        VenueAccessMode mode,
        String baseUrl,
        String apiKey,
        String secretKey,
        String passphrase
    )
    {
        public boolean credentialsLoaded()
        {
            return hasText( apiKey ) && hasText( secretKey ) && ( !requiresPassphrase( venue ) || hasText( passphrase ) );
        }

        private static boolean hasText( String value )
        {
            return value != null && !value.isBlank();
        }
    }

    private final Map<String, VenueCredentialCheckPort> checkersByVenue;
    private final VenueProfileJpaRepository repository;
    private final Environment environment;

    public VenueProfileService(
        List<VenueCredentialCheckPort> checkers,
        VenueProfileJpaRepository repository,
        Environment environment
    )
    {
        this.checkersByVenue = checkers.stream()
                                       .collect( Collectors.toUnmodifiableMap(
                                           checker -> checker.venue().toLowerCase( Locale.ROOT ),
                                           checker -> checker
                                       ) );
        this.repository = repository;
        this.environment = environment;
    }

    @Transactional(readOnly = true)
    public VenueAccessProfile getProfile( String rawVenue )
    {
        String venue = normalizeVenue( rawVenue );
        VenueProfileEntity entity = repository.findById( venue ).orElse( null );
        VenueCredentialCheckPort checker = checkersByVenue.get( venue );
        List<VenueAccessMode> availableModes = checker == null || checker.supportedModes().isEmpty()
                                               ? List.of( VenueAccessMode.PRODUCTION )
                                               : checker.supportedModes();
        GlobalAccessProfile globalAccessProfile = getGlobalAccessProfile();
        VenueAccessMode mode = resolveModeForVenue( venue, availableModes, globalAccessProfile.mode() );
        ResolvedCredentials credentials = resolveCredentials( venue, mode );
        return new VenueAccessProfile(
            venue,
            mode,
            globalAccessProfile.modeOverridden(),
            availableModes,
            hasText( credentials.apiKey() ),
            hasText( credentials.secretKey() ),
            hasText( credentials.passphrase() ),
            entity == null || entity.getConnectionStatus() == null ? VenueConnectionStatus.NOT_CONNECTED : entity.getConnectionStatus(),
            entity == null ? "Ключи не подключены." : entity.getConnectionMessage(),
            entity == null ? null : entity.getLastConnectionHttpStatus(),
            entity == null ? null : entity.getLastCheckedAt(),
            entity == null ? null : entity.getDefaultManualLatencyAdjustmentMs()
        );
    }

    @Transactional(readOnly = true)
    public GlobalAccessProfile getGlobalAccessProfile()
    {
        VenueProfileEntity entity = repository.findById( GLOBAL_PROFILE_KEY ).orElse( null );
        VenueAccessMode mode = entity != null && entity.getSelectedMode() != null
                               ? entity.getSelectedMode()
                               : defaultGlobalMode();
        return new GlobalAccessProfile(
            mode,
            entity != null && entity.getSelectedMode() != null,
            List.of( VenueAccessMode.TESTNET, VenueAccessMode.PRODUCTION )
        );
    }

    @Transactional(readOnly = true)
    public Map<String, VenueAccessProfile> getProfiles( Set<String> venues )
    {
        Map<String, VenueAccessProfile> profiles = new LinkedHashMap<>();
        venues.stream().map( VenueProfileService::normalizeVenue ).sorted().forEach( venue -> profiles.put( venue, getProfile( venue ) ) );
        return profiles;
    }

    @Transactional
    public VenueAccessProfile setMode( String rawVenue, VenueAccessMode mode )
    {
        setGlobalMode( mode );
        return getProfile( rawVenue );
    }

    @Transactional
    public GlobalAccessProfile setGlobalMode( VenueAccessMode mode )
    {
        VenueProfileEntity entity = repository.findById( GLOBAL_PROFILE_KEY ).orElseGet( () -> {
            VenueProfileEntity created = new VenueProfileEntity();
            created.setVenue( GLOBAL_PROFILE_KEY );
            return created;
        } );
        entity.setSelectedMode( mode );
        entity.setConnectionStatus( VenueConnectionStatus.NOT_CONNECTED );
        entity.setConnectionMessage( "Глобальный access mode обновлён." );
        entity.setLastConnectionHttpStatus( null );
        entity.setLastCheckedAt( null );
        repository.save( entity );
        return getGlobalAccessProfile();
    }

    @Transactional
    public VenueAccessProfile updateDefaultLatency( String rawVenue, Long defaultManualLatencyAdjustmentMs )
    {
        String venue = normalizeVenue( rawVenue );
        VenueProfileEntity entity = repository.findById( venue ).orElseGet( () -> {
            VenueProfileEntity created = new VenueProfileEntity();
            created.setVenue( venue );
            created.setConnectionStatus( VenueConnectionStatus.NOT_CONNECTED );
            created.setSelectedMode( resolveModeForVenue( venue, availableModes( venue ), getGlobalAccessProfile().mode() ) );
            return created;
        } );
        entity.setDefaultManualLatencyAdjustmentMs( defaultManualLatencyAdjustmentMs );
        repository.save( entity );
        return getProfile( venue );
    }

    @Transactional
    public VenueAccessProfile saveCheckResult( String rawVenue, VenueCredentialCheckPort.Result result )
    {
        String venue = normalizeVenue( rawVenue );
        VenueProfileEntity entity = repository.findById( venue ).orElseGet( () -> {
            VenueProfileEntity created = new VenueProfileEntity();
            created.setVenue( venue );
            created.setSelectedMode( resolveModeForVenue( venue, availableModes( venue ), getGlobalAccessProfile().mode() ) );
            return created;
        } );
        entity.setConnectionStatus( result.status() );
        entity.setConnectionMessage( result.message() );
        entity.setLastConnectionHttpStatus( result.httpStatus() );
        entity.setLastCheckedAt( Instant.now() );
        repository.save( entity );
        return getProfile( venue );
    }

    @Transactional(readOnly = true)
    public ResolvedCredentials resolveCredentials( String rawVenue )
    {
        VenueAccessProfile profile = getProfile( rawVenue );
        return resolveCredentials( profile.venue(), profile.mode() );
    }

    public String resolveProductionBaseUrl( String rawVenue )
    {
        String venue = normalizeVenue( rawVenue );
        return environment.getProperty( "trading." + venue + ".production.base-url" );
    }

    @Transactional(readOnly = true)
    public boolean hasChecker( String rawVenue )
    {
        return checkersByVenue.containsKey( normalizeVenue( rawVenue ) );
    }

    @Transactional(readOnly = true)
    public VenueCredentialCheckPort checker( String rawVenue )
    {
        VenueCredentialCheckPort checker = checkersByVenue.get( normalizeVenue( rawVenue ) );
        if( checker == null )
        {
            throw new ResourceNotFoundException( "Проверка ключей не поддерживается для площадки: " + rawVenue );
        }
        return checker;
    }

    @Transactional(readOnly = true)
    public List<VenueAccessMode> availableModes( String rawVenue )
    {
        VenueCredentialCheckPort checker = checkersByVenue.get( normalizeVenue( rawVenue ) );
        return checker == null || checker.supportedModes().isEmpty()
               ? List.of( VenueAccessMode.PRODUCTION )
               : checker.supportedModes();
    }

    private ResolvedCredentials resolveCredentials( String venue, VenueAccessMode mode )
    {
        String modeValue = mode.propertyValue();
        return new ResolvedCredentials(
            venue,
            mode,
            environment.getProperty( "trading." + venue + "." + modeValue + ".base-url" ),
            environment.getProperty( "trading." + venue + "." + modeValue + ".api-key" ),
            environment.getProperty( "trading." + venue + "." + modeValue + ".secret-key" ),
            environment.getProperty( "trading." + venue + "." + modeValue + ".passphrase" )
        );
    }

    private VenueAccessMode defaultMode( String venue, List<VenueAccessMode> availableModes )
    {
        String rawMode = environment.getProperty( "trading." + venue + ".mode", availableModes.getFirst().propertyValue() );
        if( rawMode == null || rawMode.isBlank() )
        {
            return availableModes.getFirst();
        }
        if( "prod".equalsIgnoreCase( rawMode ) || "production".equalsIgnoreCase( rawMode ) )
        {
            return VenueAccessMode.PRODUCTION;
        }
        return VenueAccessMode.TESTNET;
    }

    private VenueAccessMode defaultGlobalMode()
    {
        String rawMode = environment.getProperty( "trading.venue-access.mode" );
        if( rawMode == null || rawMode.isBlank() )
        {
            rawMode = environment.getProperty( "trading.bybit.mode", VenueAccessMode.PRODUCTION.propertyValue() );
        }
        if( "prod".equalsIgnoreCase( rawMode ) || "production".equalsIgnoreCase( rawMode ) )
        {
            return VenueAccessMode.PRODUCTION;
        }
        return VenueAccessMode.TESTNET;
    }

    private VenueAccessMode resolveModeForVenue( String venue, List<VenueAccessMode> availableModes, VenueAccessMode globalMode )
    {
        if( availableModes.contains( globalMode ) )
        {
            return globalMode;
        }
        if( availableModes.contains( VenueAccessMode.PRODUCTION ) )
        {
            return VenueAccessMode.PRODUCTION;
        }
        return defaultMode( venue, availableModes );
    }

    private static boolean hasText( String value )
    {
        return value != null && !value.isBlank();
    }

    private static boolean requiresPassphrase( String venue )
    {
        return "bitget".equalsIgnoreCase( venue )
               || "okx".equalsIgnoreCase( venue )
               || "kucoin".equalsIgnoreCase( venue );
    }

    private static String normalizeVenue( String rawVenue )
    {
        if( rawVenue == null || rawVenue.isBlank() )
        {
            throw new IllegalArgumentException( "venue must not be blank" );
        }
        return rawVenue.trim().toLowerCase( Locale.ROOT );
    }
}
