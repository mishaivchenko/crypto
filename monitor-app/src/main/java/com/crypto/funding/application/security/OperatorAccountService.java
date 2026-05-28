package com.crypto.funding.application.security;

import com.crypto.funding.infrastructure.persistence.model.OperatorAccountEntity;
import com.crypto.funding.infrastructure.persistence.repository.OperatorAccountJpaRepository;
import com.crypto.funding.security.OperatorPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class OperatorAccountService
{
    private final OperatorAccountJpaRepository repository;

    public OperatorAccountService( OperatorAccountJpaRepository repository )
    {
        this.repository = repository;
    }

    @Transactional
    public Optional<OperatorPrincipal> findOrProvision( String email )
    {
        var existing = repository.findByUsername( email );
        if( existing.isPresent() )
        {
            var entity = existing.get();
            if( !entity.isEnabled() )
            {
                return Optional.empty();
            }
            return Optional.of( new OperatorPrincipal( entity.getId(), entity.getUsername() ) );
        }
        OperatorAccountEntity entity = new OperatorAccountEntity();
        entity.setUsername( email );
        entity.setEnabled( true );
        repository.save( entity );
        return Optional.of( new OperatorPrincipal( entity.getId(), entity.getUsername() ) );
    }

    @Transactional
    public void renameUsername( String oldUsername, String newUsername )
    {
        repository.findByUsername( oldUsername ).ifPresent( entity ->
        {
            entity.setUsername( newUsername );
            repository.save( entity );
        } );
    }
}
