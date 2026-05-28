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
        return repository.findByUsername( email )
                         .map( entity -> entity.isEnabled()
                             ? new OperatorPrincipal( entity.getId(), entity.getUsername() )
                             : null )
                         .or( () ->
                         {
                             OperatorAccountEntity entity = new OperatorAccountEntity();
                             entity.setUsername( email );
                             entity.setEnabled( true );
                             repository.save( entity );
                             return Optional.of( new OperatorPrincipal( entity.getId(), entity.getUsername() ) );
                         } );
    }
}
