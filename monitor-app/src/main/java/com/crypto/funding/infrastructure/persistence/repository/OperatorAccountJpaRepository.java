package com.crypto.funding.infrastructure.persistence.repository;

import com.crypto.funding.infrastructure.persistence.model.OperatorAccountEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperatorAccountJpaRepository extends JpaRepository<OperatorAccountEntity, Long> {
    Optional<OperatorAccountEntity> findByTokenHashAndEnabledTrue(String tokenHash);

    Optional<OperatorAccountEntity> findByUsername(String username);
}
