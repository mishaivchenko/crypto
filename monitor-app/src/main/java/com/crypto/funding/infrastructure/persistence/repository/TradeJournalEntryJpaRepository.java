package com.crypto.funding.infrastructure.persistence.repository;

import com.crypto.funding.domain.trade.TradeJournalEntityType;
import com.crypto.funding.infrastructure.persistence.model.TradeJournalEntryEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeJournalEntryJpaRepository extends JpaRepository<TradeJournalEntryEntity, Long> {
    List<TradeJournalEntryEntity> findAllByEntityTypeAndEntityIdOrderByCreatedAtAsc(
            TradeJournalEntityType entityType, Long entityId);

    void deleteAllByEntityTypeAndEntityId(TradeJournalEntityType entityType, Long entityId);
}
