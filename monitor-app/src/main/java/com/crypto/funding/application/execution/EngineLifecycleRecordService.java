package com.crypto.funding.application.execution;

import com.crypto.funding.application.DomainValidationException;
import com.crypto.funding.application.ResourceNotFoundException;
import com.crypto.funding.contract.engine.EnginePositionRecordRequest;
import com.crypto.funding.contract.engine.EnginePositionResponse;
import com.crypto.funding.contract.engine.EngineTradeOutcomeRecordRequest;
import com.crypto.funding.contract.engine.EngineTradeOutcomeResponse;
import com.crypto.funding.contract.engine.EngineTradeStateUpdateRequest;
import com.crypto.funding.contract.engine.EngineTradeStateUpdateResponse;
import com.crypto.funding.domain.trade.ArmedTradeState;
import com.crypto.funding.domain.trade.PositionState;
import com.crypto.funding.infrastructure.persistence.model.ArmedTradeEntity;
import com.crypto.funding.infrastructure.persistence.model.PositionEntity;
import com.crypto.funding.infrastructure.persistence.model.TradeOutcomeEntity;
import com.crypto.funding.infrastructure.persistence.repository.ArmedTradeJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.PositionJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.TradeOutcomeJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class EngineLifecycleRecordService
{
    private final ArmedTradeJpaRepository armedTradeRepository;
    private final PositionJpaRepository positionRepository;
    private final TradeOutcomeJpaRepository tradeOutcomeRepository;

    public EngineLifecycleRecordService(
        ArmedTradeJpaRepository armedTradeRepository,
        PositionJpaRepository positionRepository,
        TradeOutcomeJpaRepository tradeOutcomeRepository
    )
    {
        this.armedTradeRepository = armedTradeRepository;
        this.positionRepository = positionRepository;
        this.tradeOutcomeRepository = tradeOutcomeRepository;
    }

    @Transactional
    public EngineTradeStateUpdateResponse updateTradeState( Long armedTradeId, EngineTradeStateUpdateRequest request )
    {
        if( armedTradeId == null )
        {
            throw new DomainValidationException( "armedTradeId must not be null." );
        }
        if( request.state() == null )
        {
            throw new DomainValidationException( "state must not be null." );
        }
        ArmedTradeEntity trade = armedTradeRepository.findById( armedTradeId )
                                                    .orElseThrow( () -> new ResourceNotFoundException(
                                                        "Prepared trade not found: " + armedTradeId
                                                    ) );
        trade.setState( request.state() );
        if( request.note() != null && !request.note().isBlank() )
        {
            trade.setNotes( request.note().trim() );
        }
        ArmedTradeEntity saved = armedTradeRepository.save( trade );
        return new EngineTradeStateUpdateResponse( saved.getId(), saved.getState(), saved.getUpdatedAt() );
    }

    @Transactional
    public EnginePositionResponse recordPosition( EnginePositionRecordRequest request )
    {
        validatePosition( request );
        ArmedTradeEntity armedTrade = armedTradeRepository.findById( request.armedTradeId() )
                                                          .orElseThrow( () -> new ResourceNotFoundException(
                                                              "Prepared trade not found: " + request.armedTradeId()
                                                          ) );
        PositionEntity entity = positionRepository.findFirstByArmedTradeIdOrderByCreatedAtDesc( request.armedTradeId() )
                                                  .orElseGet( PositionEntity::new );
        entity.setArmedTradeId( request.armedTradeId() );
        entity.setVenue( request.venue().trim().toLowerCase() );
        entity.setSymbol( request.symbol().trim().toUpperCase() );
        entity.setSide( request.side() );
        entity.setQuantity( request.quantity() );
        entity.setEntryPrice( request.entryPrice() );
        entity.setExitPrice( request.exitPrice() );
        entity.setState( request.state() );
        entity.setOpenedAt( request.openedAt() );
        entity.setClosedAt( request.closedAt() );
        EnginePositionResponse response = toPositionResponse( positionRepository.save( entity ) );
        if( request.state() == PositionState.CLOSED )
        {
            armedTrade.setState( ArmedTradeState.CLOSED );
            if( armedTrade.getNotes() == null || armedTrade.getNotes().isBlank() || "entry filled".equalsIgnoreCase( armedTrade.getNotes() ) )
            {
                armedTrade.setNotes( "exit filled" );
            }
            armedTradeRepository.save( armedTrade );
        }
        return response;
    }

    @Transactional
    public EngineTradeOutcomeResponse recordTradeOutcome( EngineTradeOutcomeRecordRequest request )
    {
        validateOutcome( request );
        assertTradeExists( request.armedTradeId() );
        TradeOutcomeEntity entity = tradeOutcomeRepository
            .findFirstByArmedTradeIdOrderByCreatedAtDesc( request.armedTradeId() )
            .orElseGet( TradeOutcomeEntity::new );
        entity.setArmedTradeId( request.armedTradeId() );
        entity.setGrossPnlUsd( request.grossPnlUsd() );
        entity.setNetPnlUsd( request.netPnlUsd() );
        entity.setFeesUsd( request.feesUsd() );
        entity.setOutcomeCode( request.outcomeCode().trim() );
        entity.setNotes( request.notes() );
        entity.setEvaluatedAt( request.evaluatedAt() == null ? Instant.now() : request.evaluatedAt() );
        return toOutcomeResponse( tradeOutcomeRepository.save( entity ) );
    }

    private void validatePosition( EnginePositionRecordRequest request )
    {
        if( request.armedTradeId() == null )
        {
            throw new DomainValidationException( "armedTradeId must not be null." );
        }
        if( request.venue() == null || request.venue().isBlank() )
        {
            throw new DomainValidationException( "venue must not be blank." );
        }
        if( request.symbol() == null || request.symbol().isBlank() )
        {
            throw new DomainValidationException( "symbol must not be blank." );
        }
        if( request.side() == null )
        {
            throw new DomainValidationException( "side must not be null." );
        }
        if( request.quantity() == null || request.quantity().compareTo( BigDecimal.ZERO ) <= 0 )
        {
            throw new DomainValidationException( "quantity must be positive." );
        }
        if( request.state() == null )
        {
            throw new DomainValidationException( "state must not be null." );
        }
    }

    private void validateOutcome( EngineTradeOutcomeRecordRequest request )
    {
        if( request.armedTradeId() == null )
        {
            throw new DomainValidationException( "armedTradeId must not be null." );
        }
        if( request.outcomeCode() == null || request.outcomeCode().isBlank() )
        {
            throw new DomainValidationException( "outcomeCode must not be blank." );
        }
        if( request.evaluatedAt() == null )
        {
            throw new DomainValidationException( "evaluatedAt must not be null." );
        }
    }

    private void assertTradeExists( Long armedTradeId )
    {
        if( !armedTradeRepository.existsById( armedTradeId ) )
        {
            throw new ResourceNotFoundException( "Prepared trade not found: " + armedTradeId );
        }
    }

    private static EnginePositionResponse toPositionResponse( PositionEntity entity )
    {
        return new EnginePositionResponse(
            entity.getId(),
            entity.getArmedTradeId(),
            entity.getVenue(),
            entity.getSymbol(),
            entity.getSide(),
            entity.getQuantity(),
            entity.getEntryPrice(),
            entity.getExitPrice(),
            entity.getState(),
            entity.getOpenedAt(),
            entity.getClosedAt(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    private static TradeOutcomeEntity toOutcomeEntity( EngineTradeOutcomeRecordRequest request )
    {
        TradeOutcomeEntity entity = new TradeOutcomeEntity();
        entity.setArmedTradeId( request.armedTradeId() );
        entity.setGrossPnlUsd( request.grossPnlUsd() );
        entity.setNetPnlUsd( request.netPnlUsd() );
        entity.setFeesUsd( request.feesUsd() );
        entity.setOutcomeCode( request.outcomeCode().trim() );
        entity.setNotes( request.notes() );
        entity.setEvaluatedAt( request.evaluatedAt() == null ? Instant.now() : request.evaluatedAt() );
        return entity;
    }

    private static EngineTradeOutcomeResponse toOutcomeResponse( TradeOutcomeEntity entity )
    {
        return new EngineTradeOutcomeResponse(
            entity.getId(),
            entity.getArmedTradeId(),
            entity.getGrossPnlUsd(),
            entity.getNetPnlUsd(),
            entity.getFeesUsd(),
            entity.getOutcomeCode(),
            entity.getNotes(),
            entity.getEvaluatedAt(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
