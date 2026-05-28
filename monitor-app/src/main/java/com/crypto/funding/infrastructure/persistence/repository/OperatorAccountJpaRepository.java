package com.crypto.funding.infrastructure.persistence.repository;

import com.crypto.funding.infrastructure.persistence.model.OperatorAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OperatorAccountJpaRepository extends JpaRepository<OperatorAccountEntity, Long>
{
    Optional<OperatorAccountEntity> findByUsername( String username );
}
