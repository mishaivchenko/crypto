package com.crypto.funding.infrastructure.persistence.repository;

import com.crypto.funding.infrastructure.persistence.model.OrderAttemptEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderAttemptJpaRepository extends JpaRepository<OrderAttemptEntity, Long>
{
    Optional<OrderAttemptEntity> findByAttemptKey( String attemptKey );

    List<OrderAttemptEntity> findAllByArmedTradeIdOrderByCreatedAtDesc( Long armedTradeId );

    List<OrderAttemptEntity> findAllByOrderByCreatedAtDesc();
}
