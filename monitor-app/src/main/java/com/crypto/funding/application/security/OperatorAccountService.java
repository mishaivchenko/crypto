package com.crypto.funding.application.security;

import com.crypto.funding.config.OperatorSecurityProperties;
import com.crypto.funding.infrastructure.persistence.model.OperatorAccountEntity;
import com.crypto.funding.infrastructure.persistence.repository.OperatorAccountJpaRepository;
import com.crypto.funding.security.OperatorPrincipal;
import com.crypto.funding.security.TokenHashing;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Optional;

@Service
public class OperatorAccountService implements ApplicationRunner
{
    private final OperatorSecurityProperties properties;
    private final OperatorAccountJpaRepository repository;

    public OperatorAccountService(
        OperatorSecurityProperties properties,
        OperatorAccountJpaRepository repository
    )
    {
        this.properties = properties;
        this.repository = repository;
    }

    @Override
    @Transactional
    public void run( ApplicationArguments args )
    {
        if( properties.getBootstrapUsers() == null || properties.getBootstrapUsers().isBlank() )
        {
            return;
        }
        Arrays.stream( properties.getBootstrapUsers().split( "," ) )
              .map( String::trim )
              .filter( value -> !value.isEmpty() )
              .forEach( this::upsertBootstrapUser );
    }

    @Transactional(readOnly = true)
    public Optional<OperatorPrincipal> authenticate( String rawToken )
    {
        if( rawToken == null || rawToken.isBlank() )
        {
            return Optional.empty();
        }
        String tokenHash = TokenHashing.sha256Hex( rawToken.trim() );
        return repository.findByTokenHashAndEnabledTrue( tokenHash )
                         .map( entity -> new OperatorPrincipal( entity.getId(), entity.getUsername() ) );
    }

    private void upsertBootstrapUser( String rawEntry )
    {
        String[] parts = rawEntry.split( ":", 2 );
        if( parts.length != 2 || parts[0].isBlank() || parts[1].isBlank() )
        {
            throw new IllegalStateException( "Invalid SECURITY_OPERATOR_BOOTSTRAP_USERS entry. Expected username:token." );
        }
        String username = parts[0].trim();
        String tokenHash = TokenHashing.normalizeHashOrHashRaw( parts[1].trim() );
        OperatorAccountEntity entity = repository.findByUsername( username ).orElseGet( OperatorAccountEntity::new );
        entity.setUsername( username );
        entity.setTokenHash( tokenHash );
        entity.setEnabled( true );
        repository.save( entity );
    }
}
