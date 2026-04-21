package com.crypto.funding.application.query;

import com.crypto.funding.application.ResourceNotFoundException;
import com.crypto.funding.application.event.FundingEventLifecycleService;
import com.crypto.funding.domain.trade.ArmedTrade;
import com.crypto.funding.infrastructure.persistence.model.ArmedTradeEntity;
import com.crypto.funding.infrastructure.persistence.repository.FundingEventJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.ArmedTradeJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TradeQueryService
{
    private final ArmedTradeJpaRepository armedTradeRepository;
    private final FundingEventJpaRepository fundingEventRepository;
    private final FundingEventLifecycleService fundingEventLifecycleService;

    public TradeQueryService(
        ArmedTradeJpaRepository armedTradeRepository,
        FundingEventJpaRepository fundingEventRepository,
        FundingEventLifecycleService fundingEventLifecycleService
    )
    {
        this.armedTradeRepository = armedTradeRepository;
        this.fundingEventRepository = fundingEventRepository;
        this.fundingEventLifecycleService = fundingEventLifecycleService;
    }

    @Transactional
    public List<ArmedTrade> listArmedTrades()
    {
        return listArmedTrades( false );
    }

    @Transactional
    public List<ArmedTrade> listArmedTrades( boolean includeHistorical )
    {
        fundingEventLifecycleService.expirePastEvents();
        List<ArmedTradeEntity> trades = armedTradeRepository.findAllByOrderByCreatedAtDesc();
        if( includeHistorical )
        {
            return trades.stream().map( this::toDomain ).toList();
        }

        Set<Long> activeFundingEventIds = fundingEventRepository.findAll()
                                                                .stream()
                                                                .filter( event -> event.getStatus() != com.crypto.funding.domain.event.FundingEventStatus.EXPIRED )
                                                                .filter( event -> event.getStatus() != com.crypto.funding.domain.event.FundingEventStatus.CANCELLED )
                                                                .map( com.crypto.funding.infrastructure.persistence.model.FundingEventEntity::getId )
                                                                .collect( Collectors.toSet() );
        return trades.stream()
                     .filter( trade -> activeFundingEventIds.contains( trade.getFundingEventId() ) )
                     .map( this::toDomain )
                     .toList();
    }

    @Transactional(readOnly = true)
    public ArmedTrade getArmedTrade( Long id )
    {
        return armedTradeRepository.findById( id )
                                   .map( this::toDomain )
                                   .orElseThrow( () -> new ResourceNotFoundException( "Подготовленная сделка не найдена: " + id ) );
    }

    private ArmedTrade toDomain( ArmedTradeEntity entity )
    {
        return new ArmedTrade(
            entity.getId(),
            entity.getFundingEventId(),
            entity.getNotionalUsd(),
            entity.getIntendedSide(),
            entity.getPlannedEntryAt(),
            entity.getPlannedExitAt(),
            entity.getArmedAt(),
            entity.getEventAgeMsAtArm(),
            entity.getEntryLeadMs(),
            entity.getExitLeadMs(),
            entity.getEntryAttemptCount(),
            entity.getEntrySpacingMs(),
            entity.getMeasuredEntryLatencyMs(),
            entity.getManualLatencyAdjustmentMs(),
            entity.getEffectiveEntryLatencyMs(),
            entity.getArmSource(),
            entity.getState(),
            entity.getNotes(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
