package com.crypto.funding.infrastructure.persistence.repository;

import com.crypto.funding.domain.trade.TradeJournalEntityType;
import com.crypto.funding.infrastructure.persistence.model.TradeJournalEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TradeJournalEntryJpaRepository extends JpaRepository<TradeJournalEntryEntity, Long>
{
    List<TradeJournalEntryEntity> findAllByEntityTypeAndEntityIdOrderByCreatedAtAsc(
        TradeJournalEntityType entityType,
        Long entityId
    );
}
