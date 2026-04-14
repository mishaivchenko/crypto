package com.crypto.funding.application.event;

import com.crypto.funding.application.DomainValidationException;
import com.crypto.funding.domain.event.FundingEvent;
import com.crypto.funding.domain.event.FundingEventStatus;
import com.crypto.funding.domain.trade.TradeJournalActorType;
import com.crypto.funding.domain.trade.TradeJournalEntityType;
import com.crypto.funding.domain.trade.TradeJournalEventCode;
import com.crypto.funding.application.trade.TradeJournalService;
import com.crypto.funding.infrastructure.persistence.mapper.FundingEventMapper;
import com.crypto.funding.infrastructure.persistence.model.FundingEventEntity;
import com.crypto.funding.infrastructure.persistence.repository.FundingEventJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
public class FundingEventCommandService
{
    private final FundingEventJpaRepository fundingEventRepository;
    private final TradeJournalService tradeJournalService;

    public FundingEventCommandService(
        FundingEventJpaRepository fundingEventRepository,
        TradeJournalService tradeJournalService
    )
    {
        this.fundingEventRepository = fundingEventRepository;
        this.tradeJournalService = tradeJournalService;
    }

    @Transactional
    public FundingEvent create( CreateFundingEventCommand command )
    {
        if( !command.fundingTime().isAfter( Instant.now() ) )
        {
            throw new DomainValidationException( "Время фандинга должно быть в будущем." );
        }

        FundingEventEntity entity = new FundingEventEntity();
        entity.setVenue( normalizeVenue( command.venue() ) );
        entity.setSymbol( normalizeSymbol( command.symbol() ) );
        entity.setFundingTime( command.fundingTime() );
        entity.setFundingRatePct( command.fundingRatePct() );
        entity.setStatus( FundingEventStatus.DISCOVERED );
        entity.setSourceType( normalizeNullable( command.sourceType() ) );
        entity.setSourceRef( normalizeNullable( command.sourceRef() ) );
        entity.setSignalCandidateId( command.signalCandidateId() );
        entity.setDiscoveredAt( Instant.now() );

        FundingEvent created = FundingEventMapper.toDomain( fundingEventRepository.save( entity ) );
        tradeJournalService.append(
            TradeJournalEntityType.FUNDING_EVENT,
            created.id(),
            TradeJournalEventCode.FUNDING_EVENT_CREATED,
            null,
            FundingEventStatus.DISCOVERED.name(),
            command.signalCandidateId() == null ? TradeJournalActorType.OPERATOR : TradeJournalActorType.SYSTEM,
            command.signalCandidateId() == null ? "api" : "candidate-review",
            command.sourceRef()
        );
        return created;
    }

    private static String normalizeVenue( String venue )
    {
        return venue.trim().toLowerCase( Locale.ROOT );
    }

    private static String normalizeSymbol( String symbol )
    {
        return symbol.trim().toUpperCase( Locale.ROOT );
    }

    private static String normalizeNullable( String value )
    {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
