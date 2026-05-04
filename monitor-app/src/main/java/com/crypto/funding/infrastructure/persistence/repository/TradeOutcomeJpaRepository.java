package com.crypto.funding.infrastructure.persistence.repository;

import com.crypto.funding.infrastructure.persistence.model.TradeOutcomeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TradeOutcomeJpaRepository extends JpaRepository<TradeOutcomeEntity, Long>
{
    Optional<TradeOutcomeEntity> findFirstByArmedTradeIdOrderByCreatedAtDesc( Long armedTradeId );
}
