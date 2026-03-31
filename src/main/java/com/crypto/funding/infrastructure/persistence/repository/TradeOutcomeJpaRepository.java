package com.crypto.funding.infrastructure.persistence.repository;

import com.crypto.funding.infrastructure.persistence.model.TradeOutcomeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeOutcomeJpaRepository extends JpaRepository<TradeOutcomeEntity, Long>
{
}
