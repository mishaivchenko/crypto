package com.crypto.funding.infrastructure.persistence.repository;

import com.crypto.funding.infrastructure.persistence.model.TradeOutcomeEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeOutcomeJpaRepository extends JpaRepository<TradeOutcomeEntity, Long> {
    Optional<TradeOutcomeEntity> findFirstByArmedTradeIdOrderByCreatedAtDesc(Long armedTradeId);

    List<TradeOutcomeEntity> findByArmedTradeIdIn(Collection<Long> armedTradeIds);
}
