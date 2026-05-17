package com.crypto.funding.application.trade;

import com.crypto.funding.application.DomainValidationException;
import com.crypto.funding.application.ResourceNotFoundException;
import com.crypto.funding.application.venue.VenueLatencyService;
import com.crypto.funding.application.venue.VenueProfileService;
import com.crypto.funding.config.MonitorRiskProperties;
import com.crypto.funding.config.TradePreparationProperties;
import com.crypto.funding.domain.event.FundingEventStatus;
import com.crypto.funding.domain.trade.ArmedTrade;
import com.crypto.funding.domain.trade.ArmedTradeState;
import com.crypto.funding.domain.trade.TradeArmSource;
import com.crypto.funding.domain.trade.TradeJournalActorType;
import com.crypto.funding.domain.trade.TradeJournalEntityType;
import com.crypto.funding.domain.trade.TradeJournalEventCode;
import com.crypto.funding.domain.trade.TradeSide;
import com.crypto.funding.infrastructure.persistence.mapper.ArmedTradeMapper;
import com.crypto.funding.infrastructure.persistence.model.ArmedTradeEntity;
import com.crypto.funding.infrastructure.persistence.model.FundingEventEntity;
import com.crypto.funding.infrastructure.persistence.repository.ArmedTradeJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.FundingEventJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;

@Service
public class ArmedTradeCommandService
{
    private static final Set<ArmedTradeState> ACTIVE_STATES = Set.of(
        ArmedTradeState.ARMED,
        ArmedTradeState.ENTRY_PENDING,
        ArmedTradeState.ENTRY_ATTEMPTED,
        ArmedTradeState.OPEN,
        ArmedTradeState.EXIT_PENDING
    );

    private final FundingEventJpaRepository fundingEventRepository;
    private final ArmedTradeJpaRepository armedTradeRepository;
    private final TradeJournalService tradeJournalService;
    private final VenueLatencyService venueLatencyService;
    private final VenueProfileService venueProfileService;
    private final TradePreparationProperties preparationProperties;
    private final MonitorRiskProperties riskProperties;

    public ArmedTradeCommandService(
        FundingEventJpaRepository fundingEventRepository,
        ArmedTradeJpaRepository armedTradeRepository,
        TradeJournalService tradeJournalService,
        VenueLatencyService venueLatencyService,
        VenueProfileService venueProfileService,
        TradePreparationProperties preparationProperties,
        MonitorRiskProperties riskProperties
    )
    {
        this.fundingEventRepository = fundingEventRepository;
        this.armedTradeRepository = armedTradeRepository;
        this.tradeJournalService = tradeJournalService;
        this.venueLatencyService = venueLatencyService;
        this.venueProfileService = venueProfileService;
        this.preparationProperties = preparationProperties;
        this.riskProperties = riskProperties;
    }

    private static final Set<ArmedTradeState> CANCELLABLE_STATES = Set.of(
        ArmedTradeState.ARMED,
        ArmedTradeState.ENTRY_PENDING,
        ArmedTradeState.ENTRY_ATTEMPTED
    );

    @Transactional
    public ArmedTrade cancel( Long id )
    {
        ArmedTradeEntity entity = armedTradeRepository.findById( id )
                                                      .orElseThrow( () -> new ResourceNotFoundException( "Подготовленная сделка не найдена: " + id ) );
        if( !CANCELLABLE_STATES.contains( entity.getState() ) )
        {
            throw new DomainValidationException( "Нельзя отменить сделку в статусе " + entity.getState() );
        }
        ArmedTradeState previousState = entity.getState();
        entity.setState( ArmedTradeState.CANCELLED );
        armedTradeRepository.save( entity );
        tradeJournalService.append(
            TradeJournalEntityType.ARMED_TRADE,
            entity.getId(),
            TradeJournalEventCode.ARMED_TRADE_CANCELLED,
            previousState.name(),
            ArmedTradeState.CANCELLED.name(),
            TradeJournalActorType.OPERATOR,
            "api",
            null
        );
        return ArmedTradeMapper.toDomain( entity );
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
                                                                   "Событие фандинга не найдено: " + command.fundingEventId()
                                                               ) );
        FundingEventStatus previousFundingEventStatus = fundingEvent.getStatus();

        if( fundingEvent.getStatus() == FundingEventStatus.CANCELLED || fundingEvent.getStatus() == FundingEventStatus.EXPIRED )
        {
            throw new DomainValidationException(
                "Событие фандинга " + command.fundingEventId() + " нельзя подготовить из статуса " + fundingEvent.getStatus()
            );
        }
        if( armedTradeRepository.existsByFundingEventIdAndStateIn( fundingEvent.getId(), ACTIVE_STATES ) )
        {
            throw new DomainValidationException( "У события фандинга " + command.fundingEventId() + " уже есть активная подготовленная сделка." );
        }
        String normalizedVenue = fundingEvent.getVenue() == null ? "" : fundingEvent.getVenue().trim().toLowerCase( Locale.ROOT );
        if( riskProperties.disabledVenues().contains( normalizedVenue ) )
        {
            throw new DomainValidationException( "Venue " + fundingEvent.getVenue() + " is disabled for trading." );
        }
        long activeTrades = armedTradeRepository.countByStateIn( ACTIVE_STATES );
        if( activeTrades >= riskProperties.getMaxConcurrentArmedTrades() )
        {
            throw new DomainValidationException(
                "Max concurrent armed trades limit reached (" + riskProperties.getMaxConcurrentArmedTrades() + "). Close existing trades first."
            );
        }
        if( command.intendedSide() != null && command.intendedSide() != TradeSide.SHORT )
        {
            throw new DomainValidationException( "Funding trades support SHORT side only." );
        }
        if( command.entryAttemptCount() != null && command.entryAttemptCount() < 1 )
        {
            throw new DomainValidationException( "entryAttemptCount must be positive." );
        }
        if( command.entrySpacingMs() != null && command.entrySpacingMs() < 0 )
        {
            throw new DomainValidationException( "entrySpacingMs must not be negative." );
        }

        ArmedTradeEntity entity = new ArmedTradeEntity();
        entity.setFundingEventId( fundingEvent.getId() );
        entity.setNotionalUsd( command.notionalUsd() );
        entity.setIntendedSide( TradeSide.SHORT );
        entity.setPlannedEntryAt( command.plannedEntryAt() );
        entity.setPlannedExitAt( command.plannedExitAt() );
        entity.setEntryAttemptCount(
            command.entryAttemptCount() == null ? preparationProperties.getDefaultEntryAttemptCount() : command.entryAttemptCount()
        );
        entity.setEntrySpacingMs(
            command.entrySpacingMs() == null ? preparationProperties.getDefaultEntrySpacingMs() : command.entrySpacingMs()
        );
        Long manualLatencyAdjustmentMs = command.manualLatencyAdjustmentMs() == null
            ? preparationProperties.getDefaultManualLatencyAdjustmentMs()
            : command.manualLatencyAdjustmentMs();
        entity.setManualLatencyAdjustmentMs( manualLatencyAdjustmentMs );
        Long measuredEntryLatencyMs = venueLatencyService.estimateEntryLatencyMs( fundingEvent.getVenue() );
        entity.setMeasuredEntryLatencyMs( measuredEntryLatencyMs );
        entity.setEffectiveEntryLatencyMs( venueLatencyService.effectiveEntryLatencyMs( measuredEntryLatencyMs, manualLatencyAdjustmentMs ) );
        Instant armedAt = Instant.now();
        entity.setArmedAt( armedAt );
        entity.setEventAgeMsAtArm( Duration.between( fundingEvent.getDiscoveredAt(), armedAt ).toMillis() );
        entity.setEntryLeadMs( command.plannedEntryAt() == null ? null : Duration.between( command.plannedEntryAt(), fundingEvent.getFundingTime() ).toMillis() );
        entity.setExitLeadMs( command.plannedExitAt() == null ? null : Duration.between( command.plannedExitAt(), fundingEvent.getFundingTime() ).toMillis() );
        entity.setArmSource( armSource );
        entity.setState( ArmedTradeState.ARMED );
        entity.setNotes( command.notes() == null || command.notes().isBlank() ? null : command.notes().trim() );
        entity.setMode( venueProfileService.getGlobalAccessProfile().mode() );
        entity.setStopLossUsd( command.stopLossUsd() );
        entity.setTakeProfitUsd( command.takeProfitUsd() );

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
