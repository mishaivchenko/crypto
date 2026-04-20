package com.crypto.funding.application.execution;

import com.crypto.funding.application.DomainValidationException;
import com.crypto.funding.application.ResourceNotFoundException;
import com.crypto.funding.contract.engine.EngineOrderAttemptRecordRequest;
import com.crypto.funding.contract.engine.EngineOrderAttemptResponse;
import com.crypto.funding.infrastructure.persistence.model.OrderAttemptEntity;
import com.crypto.funding.infrastructure.persistence.repository.ArmedTradeJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.OrderAttemptJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class OrderAttemptRecordService
{
    private final OrderAttemptJpaRepository orderAttemptRepository;
    private final ArmedTradeJpaRepository armedTradeRepository;

    public OrderAttemptRecordService(
        OrderAttemptJpaRepository orderAttemptRepository,
        ArmedTradeJpaRepository armedTradeRepository
    )
    {
        this.orderAttemptRepository = orderAttemptRepository;
        this.armedTradeRepository = armedTradeRepository;
    }

    @Transactional
    public EngineOrderAttemptResponse record( EngineOrderAttemptRecordRequest request )
    {
        validate( request );
        if( !armedTradeRepository.existsById( request.armedTradeId() ) )
        {
            throw new ResourceNotFoundException( "Prepared trade not found: " + request.armedTradeId() );
        }

        return orderAttemptRepository.findByAttemptKey( request.attemptKey() )
                                     .map( this::toResponse )
                                     .orElseGet( () -> toResponse( orderAttemptRepository.save( toEntity( request ) ) ) );
    }

    @Transactional(readOnly = true)
    public List<EngineOrderAttemptResponse> listAll()
    {
        return orderAttemptRepository.findAllByOrderByCreatedAtDesc().stream().map( this::toResponse ).toList();
    }

    @Transactional(readOnly = true)
    public List<EngineOrderAttemptResponse> listByArmedTrade( Long armedTradeId )
    {
        if( !armedTradeRepository.existsById( armedTradeId ) )
        {
            throw new ResourceNotFoundException( "Prepared trade not found: " + armedTradeId );
        }
        return orderAttemptRepository.findAllByArmedTradeIdOrderByCreatedAtDesc( armedTradeId )
                                     .stream()
                                     .map( this::toResponse )
                                     .toList();
    }

    private OrderAttemptEntity toEntity( EngineOrderAttemptRecordRequest request )
    {
        OrderAttemptEntity entity = new OrderAttemptEntity();
        entity.setAttemptKey( request.attemptKey().trim() );
        entity.setArmedTradeId( request.armedTradeId() );
        entity.setAttemptNumber( request.attemptNumber() );
        entity.setVenue( request.venue().trim().toLowerCase() );
        entity.setSymbol( request.symbol().trim().toUpperCase() );
        entity.setSide( request.side() );
        entity.setExecutionType( request.executionType() );
        entity.setQuantity( request.quantity() );
        entity.setLimitPrice( request.limitPrice() );
        entity.setStatus( request.status() );
        entity.setExternalOrderId( request.externalOrderId() );
        entity.setTargetEntryAt( request.targetEntryAt() );
        entity.setTriggerAt( request.triggerAt() );
        entity.setSubmittedAt( request.submittedAt() );
        entity.setExchangeTimestamp( request.exchangeTimestamp() );
        entity.setFailureReason( request.failureReason() );
        return entity;
    }

    private EngineOrderAttemptResponse toResponse( OrderAttemptEntity entity )
    {
        return new EngineOrderAttemptResponse(
            entity.getId(),
            entity.getAttemptKey(),
            entity.getArmedTradeId(),
            entity.getAttemptNumber(),
            entity.getVenue(),
            entity.getSymbol(),
            entity.getSide(),
            entity.getExecutionType(),
            entity.getQuantity(),
            entity.getLimitPrice(),
            entity.getStatus(),
            entity.getExternalOrderId(),
            entity.getTargetEntryAt(),
            entity.getTriggerAt(),
            entity.getSubmittedAt(),
            entity.getExchangeTimestamp(),
            entity.getFailureReason(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    private void validate( EngineOrderAttemptRecordRequest request )
    {
        if( request.attemptKey() == null || request.attemptKey().isBlank() )
        {
            throw new DomainValidationException( "attemptKey must not be blank." );
        }
        if( request.armedTradeId() == null )
        {
            throw new DomainValidationException( "armedTradeId must not be null." );
        }
        if( request.attemptNumber() != null && request.attemptNumber() < 1 )
        {
            throw new DomainValidationException( "attemptNumber must be positive." );
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
        if( request.executionType() == null )
        {
            throw new DomainValidationException( "executionType must not be null." );
        }
        if( request.quantity() == null || request.quantity().compareTo( BigDecimal.ZERO ) <= 0 )
        {
            throw new DomainValidationException( "quantity must be positive." );
        }
        if( request.status() == null )
        {
            throw new DomainValidationException( "status must not be null." );
        }
    }
}
