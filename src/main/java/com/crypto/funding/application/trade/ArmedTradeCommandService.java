package com.crypto.funding.application.trade;

import com.crypto.funding.application.DomainValidationException;
import com.crypto.funding.application.ResourceNotFoundException;
import com.crypto.funding.domain.event.FundingEventStatus;
import com.crypto.funding.domain.trade.ArmedTrade;
import com.crypto.funding.domain.trade.ArmedTradeState;
import com.crypto.funding.domain.trade.TradeArmSource;
import com.crypto.funding.domain.trade.TradeJournalActorType;
import com.crypto.funding.domain.trade.TradeJournalEntityType;
import com.crypto.funding.domain.trade.TradeJournalEventCode;
import com.crypto.funding.infrastructure.persistence.mapper.ArmedTradeMapper;
import com.crypto.funding.infrastructure.persistence.model.ArmedTradeEntity;
import com.crypto.funding.infrastructure.persistence.model.FundingEventEntity;
import com.crypto.funding.infrastructure.persistence.repository.ArmedTradeJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.FundingEventJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

@Service
public class ArmedTradeCommandService
{
    private final FundingEventJpaRepository fundingEventRepository;
    private final ArmedTradeJpaRepository armedTradeRepository;
    private final TradeJournalService tradeJournalService;

    public ArmedTradeCommandService(
        FundingEventJpaRepository fundingEventRepository,
        ArmedTradeJpaRepository armedTradeRepository,
        TradeJournalService tradeJournalService
    )
    {
        this.fundingEventRepository = fundingEventRepository;
        this.armedTradeRepository = armedTradeRepository;
        this.tradeJournalService = tradeJournalService;
    }

    @Transactional
    public ArmedTrade create( CreateArmedTradeCommand command )
    {
        return create( command, TradeArmSource.DIRECT_TRADE_API, TradeJournalActorType.OPERATOR, "api" );
    }

    @Transactional
    public ArmedTrade create(
        CreateArmedTradeCommand command,
        TradeArmSource armSource,
        TradeJournalActorType actorType,
        String actorRef
    )
    {
        FundingEventEntity fundingEvent = fundingEventRepository.findById( command.fundingEventId() )
                                                               .orElseThrow( () -> new ResourceNotFoundException(
                                                                   "FundingEvent not found: " + command.fundingEventId()
                                                               ) );
        FundingEventStatus previousFundingEventStatus = fundingEvent.getStatus();

        if( fundingEvent.getStatus() == FundingEventStatus.CANCELLED || fundingEvent.getStatus() == FundingEventStatus.EXPIRED )
        {
            throw new DomainValidationException(
                "FundingEvent " + command.fundingEventId() + " is not armable from status " + fundingEvent.getStatus()
            );
        }
        if( armedTradeRepository.existsByFundingEventIdAndStateIn(
            fundingEvent.getId(),
            Set.of( ArmedTradeState.ARMED, ArmedTradeState.ENTRY_PENDING, ArmedTradeState.ENTRY_ATTEMPTED, ArmedTradeState.OPEN, ArmedTradeState.EXIT_PENDING )
        ) )
        {
            throw new DomainValidationException( "FundingEvent " + command.fundingEventId() + " already has an active ArmedTrade" );
        }

        ArmedTradeEntity entity = new ArmedTradeEntity();
        entity.setFundingEventId( fundingEvent.getId() );
        entity.setNotionalUsd( command.notionalUsd() );
        entity.setIntendedSide( command.intendedSide() );
        entity.setPlannedEntryAt( command.plannedEntryAt() );
        entity.setPlannedExitAt( command.plannedExitAt() );
        Instant armedAt = Instant.now();
        entity.setArmedAt( armedAt );
        entity.setEventAgeMsAtArm( Duration.between( fundingEvent.getDiscoveredAt(), armedAt ).toMillis() );
        entity.setEntryLeadMs( command.plannedEntryAt() == null ? null : Duration.between( fundingEvent.getFundingTime(), command.plannedEntryAt() ).toMillis() );
        entity.setExitLeadMs( command.plannedExitAt() == null ? null : Duration.between( fundingEvent.getFundingTime(), command.plannedExitAt() ).toMillis() );
        entity.setArmSource( armSource );
        entity.setState( ArmedTradeState.ARMED );
        entity.setNotes( command.notes() == null || command.notes().isBlank() ? null : command.notes().trim() );

        ArmedTradeEntity saved = armedTradeRepository.save( entity );
        fundingEvent.setStatus( FundingEventStatus.ARMED );
        fundingEventRepository.save( fundingEvent );
        if( previousFundingEventStatus != FundingEventStatus.ARMED )
        {
            tradeJournalService.append(
                TradeJournalEntityType.FUNDING_EVENT,
                fundingEvent.getId(),
                TradeJournalEventCode.FUNDING_EVENT_ARMED,
                previousFundingEventStatus.name(),
                FundingEventStatus.ARMED.name(),
                actorType,
                actorRef,
                saved.getNotes()
            );
        }
        tradeJournalService.append(
            TradeJournalEntityType.ARMED_TRADE,
            saved.getId(),
            TradeJournalEventCode.ARMED_TRADE_CREATED,
            null,
            ArmedTradeState.ARMED.name(),
            actorType,
            actorRef,
            saved.getNotes()
        );
        return ArmedTradeMapper.toDomain( saved );
    }
}
