package com.crypto.funding.application.trade;

import com.crypto.funding.domain.trade.TradeJournalActorType;
import com.crypto.funding.domain.trade.TradeJournalEntry;
import com.crypto.funding.domain.trade.TradeJournalEntityType;
import com.crypto.funding.domain.trade.TradeJournalEventCode;
import com.crypto.funding.infrastructure.persistence.mapper.TradeJournalEntryMapper;
import com.crypto.funding.infrastructure.persistence.model.TradeJournalEntryEntity;
import com.crypto.funding.infrastructure.persistence.repository.TradeJournalEntryJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TradeJournalService
{
    private final TradeJournalEntryJpaRepository repository;

    public TradeJournalService( TradeJournalEntryJpaRepository repository )
    {
        this.repository = repository;
    }

    @Transactional
    public TradeJournalEntry append(
        TradeJournalEntityType entityType,
        Long entityId,
        TradeJournalEventCode eventCode,
        String oldState,
        String newState,
        TradeJournalActorType actorType,
        String actorRef,
        String note
    )
    {
        TradeJournalEntryEntity entity = new TradeJournalEntryEntity();
        entity.setEntityType( entityType );
        entity.setEntityId( entityId );
        entity.setEventCode( eventCode );
        entity.setOldState( oldState );
        entity.setNewState( newState );
        entity.setActorType( actorType );
        entity.setActorRef( actorRef );
        entity.setNote( note );
        return TradeJournalEntryMapper.toDomain( repository.save( entity ) );
    }

    @Transactional(readOnly = true)
    public List<TradeJournalEntry> list( TradeJournalEntityType entityType, Long entityId )
    {
        return repository.findAllByEntityTypeAndEntityIdOrderByCreatedAtAsc( entityType, entityId )
                         .stream()
                         .map( TradeJournalEntryMapper::toDomain )
                         .toList();
    }
}
