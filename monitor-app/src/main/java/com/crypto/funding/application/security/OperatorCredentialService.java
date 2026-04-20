package com.crypto.funding.application.security;

import com.crypto.funding.application.DomainValidationException;
import com.crypto.funding.application.ResourceNotFoundException;
import com.crypto.funding.application.port.CredentialCipherPort;
import com.crypto.funding.application.port.VenueCredentialCheckPort;
import com.crypto.funding.config.CredentialStorageProperties;
import com.crypto.funding.domain.venue.VenueAccessMode;
import com.crypto.funding.domain.venue.VenueConnectionStatus;
import com.crypto.funding.infrastructure.persistence.model.OperatorExchangeCredentialEntity;
import com.crypto.funding.infrastructure.persistence.repository.OperatorExchangeCredentialJpaRepository;
import com.crypto.funding.security.OperatorContext;
import com.crypto.funding.security.OperatorPrincipal;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OperatorCredentialService
{
    public record CredentialSummary(
        String venue,
        VenueAccessMode mode,
        boolean configured,
        String apiKeyMask,
        String secretKeyMask,
        String passphraseMask,
        VenueConnectionStatus connectionStatus,
        String connectionMessage,
        Integer lastConnectionHttpStatus,
        Instant lastCheckedAt,
        Instant updatedAt
    )
    {
    }

    public record UpdateCredentialCommand(
        String apiKey,
        String secretKey,
        String passphrase
    )
    {
    }

    private final CredentialStorageProperties properties;
    private final CredentialCipherPort cipher;
    private final OperatorExchangeCredentialJpaRepository repository;
    private final Map<String, VenueCredentialCheckPort> checkersByVenue;
    private final Environment environment;

    public OperatorCredentialService(
        CredentialStorageProperties properties,
        CredentialCipherPort cipher,
        OperatorExchangeCredentialJpaRepository repository,
        List<VenueCredentialCheckPort> checkers,
        Environment environment
    )
    {
        this.properties = properties;
        this.cipher = cipher;
        this.repository = repository;
        this.checkersByVenue = checkers.stream()
                                       .collect( Collectors.toUnmodifiableMap(
                                           checker -> normalizeVenue( checker.venue() ),
                                           checker -> checker
                                       ) );
        this.environment = environment;
    }

    @Transactional(readOnly = true)
    public List<CredentialSummary> listMine()
    {
        OperatorPrincipal operator = OperatorContext.require();
        return repository.findAllByOperatorIdOrderByVenueAscModeAsc( operator.id() )
                         .stream()
                         .map( this::toSummary )
                         .toList();
    }

    @Transactional(readOnly = true)
    public CredentialSummary currentOperatorSummary( String rawVenue, VenueAccessMode mode )
    {
        String venue = normalizeVenue( rawVenue );
        if( !properties.isEnabled() )
        {
            return notConfigured( venue, mode, "Credential storage is disabled." );
        }
        return OperatorContext.current()
                              .flatMap( operator -> repository.findByOperatorIdAndVenueAndMode( operator.id(), venue, mode ) )
                              .map( this::toSummary )
                              .orElseGet( () -> notConfigured( venue, mode, "Ключи не подключены." ) );
    }

    @Transactional
    public CredentialSummary upsertMine( String rawVenue, VenueAccessMode mode, UpdateCredentialCommand command )
    {
        ensureStorageEnabled();
        OperatorPrincipal operator = OperatorContext.require();
        String venue = normalizeVenue( rawVenue );
        OperatorExchangeCredentialEntity entity = repository.findByOperatorIdAndVenueAndMode( operator.id(), venue, mode )
                                                            .orElseGet( OperatorExchangeCredentialEntity::new );
        entity.setOperatorId( operator.id() );
        entity.setVenue( venue );
        entity.setMode( mode );
        entity.setApiKeyCiphertext( cipher.encrypt( requireSecretPart( command.apiKey(), "apiKey" ) ) );
        entity.setSecretKeyCiphertext( cipher.encrypt( requireSecretPart( command.secretKey(), "secretKey" ) ) );
        entity.setPassphraseCiphertext( cipher.encrypt( normalizeOptional( command.passphrase() ) ) );
        entity.setApiKeyMask( mask( command.apiKey() ) );
        entity.setSecretKeyMask( mask( command.secretKey() ) );
        entity.setPassphraseMask( mask( command.passphrase() ) );
        entity.setConnectionStatus( VenueConnectionStatus.NOT_CONNECTED );
        entity.setConnectionMessage( "Ключи сохранены, проверка ещё не запускалась." );
        entity.setLastConnectionHttpStatus( null );
        entity.setLastCheckedAt( null );
        return toSummary( repository.save( entity ) );
    }

    @Transactional
    public void deleteMine( String rawVenue, VenueAccessMode mode )
    {
        OperatorPrincipal operator = OperatorContext.require();
        repository.deleteByOperatorIdAndVenueAndMode( operator.id(), normalizeVenue( rawVenue ), mode );
    }

    @Transactional
    public CredentialSummary checkMine( String rawVenue, VenueAccessMode mode )
    {
        ensureStorageEnabled();
        OperatorPrincipal operator = OperatorContext.require();
        String venue = normalizeVenue( rawVenue );
        OperatorExchangeCredentialEntity entity = repository.findByOperatorIdAndVenueAndMode( operator.id(), venue, mode )
                                                            .orElseThrow( () -> new ResourceNotFoundException(
                                                                "Ключи не найдены для " + venue + " / " + mode
                                                            ) );
        VenueCredentialCheckPort checker = checkersByVenue.get( venue );
        if( checker == null )
        {
            entity.setConnectionStatus( VenueConnectionStatus.UNSUPPORTED );
            entity.setConnectionMessage( "Проверка ключей не поддерживается для площадки: " + venue );
            entity.setLastCheckedAt( Instant.now() );
            return toSummary( repository.save( entity ) );
        }

        try
        {
            VenueCredentialCheckPort.Result result = checker.check( new VenueCredentialCheckPort.Credentials(
                venue,
                mode,
                baseUrl( venue, mode ),
                cipher.decrypt( entity.getApiKeyCiphertext() ),
                cipher.decrypt( entity.getSecretKeyCiphertext() ),
                cipher.decrypt( entity.getPassphraseCiphertext() )
            ) );
            entity.setConnectionStatus( result.status() );
            entity.setConnectionMessage( result.message() );
            entity.setLastConnectionHttpStatus( result.httpStatus() );
        }
        catch( Exception ex )
        {
            entity.setConnectionStatus( VenueConnectionStatus.ERROR );
            entity.setConnectionMessage( ex.getMessage() );
            entity.setLastConnectionHttpStatus( null );
        }
        entity.setLastCheckedAt( Instant.now() );
        return toSummary( repository.save( entity ) );
    }

    private CredentialSummary toSummary( OperatorExchangeCredentialEntity entity )
    {
        return new CredentialSummary(
            entity.getVenue(),
            entity.getMode(),
            entity.getApiKeyCiphertext() != null && entity.getSecretKeyCiphertext() != null,
            entity.getApiKeyMask(),
            entity.getSecretKeyMask(),
            entity.getPassphraseMask(),
            entity.getConnectionStatus(),
            entity.getConnectionMessage(),
            entity.getLastConnectionHttpStatus(),
            entity.getLastCheckedAt(),
            entity.getUpdatedAt()
        );
    }

    private CredentialSummary notConfigured( String venue, VenueAccessMode mode, String message )
    {
        return new CredentialSummary(
            venue,
            mode,
            false,
            null,
            null,
            null,
            VenueConnectionStatus.NOT_CONNECTED,
            message,
            null,
            null,
            null
        );
    }

    private void ensureStorageEnabled()
    {
        if( !properties.isEnabled() )
        {
            throw new DomainValidationException( "Credential storage is disabled." );
        }
    }

    private String baseUrl( String venue, VenueAccessMode mode )
    {
        return environment.getProperty( "trading." + venue + "." + mode.propertyValue() + ".base-url" );
    }

    private static String requireSecretPart( String value, String field )
    {
        if( value == null || value.isBlank() )
        {
            throw new DomainValidationException( field + " must not be blank." );
        }
        return value.trim();
    }

    private static String normalizeOptional( String value )
    {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeVenue( String rawVenue )
    {
        if( rawVenue == null || rawVenue.isBlank() )
        {
            throw new IllegalArgumentException( "venue must not be blank" );
        }
        return rawVenue.trim().toLowerCase( Locale.ROOT );
    }

    private static String mask( String value )
    {
        if( value == null || value.isBlank() )
        {
            return null;
        }
        String trimmed = value.trim();
        if( trimmed.length() <= 4 )
        {
            return "****";
        }
        return "****" + trimmed.substring( trimmed.length() - 4 );
    }
}
