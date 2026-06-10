package com.crypto.funding.application.event;

import com.crypto.funding.application.ResourceNotFoundException;
import com.crypto.funding.domain.event.FundingEvent;
import com.crypto.funding.domain.event.FundingEventStatus;
import com.crypto.funding.infrastructure.persistence.mapper.FundingEventMapper;
import com.crypto.funding.infrastructure.persistence.model.FundingEventEntity;
import com.crypto.funding.infrastructure.persistence.repository.ArmedTradeJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.FundingEventJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.LiquidityAssessmentJpaRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class FundingEventQueryService
{
    private final FundingEventJpaRepository fundingEventRepository;
    private final ArmedTradeJpaRepository armedTradeRepository;
    private final LiquidityAssessmentJpaRepository liquidityAssessmentRepository;
    private final FundingEventLifecycleService fundingEventLifecycleService;

    public FundingEventQueryService(
        FundingEventJpaRepository fundingEventRepository,
        ArmedTradeJpaRepository armedTradeRepository,
        LiquidityAssessmentJpaRepository liquidityAssessmentRepository,
        FundingEventLifecycleService fundingEventLifecycleService
    )
    {
        this.fundingEventRepository = fundingEventRepository;
        this.armedTradeRepository = armedTradeRepository;
        this.liquidityAssessmentRepository = liquidityAssessmentRepository;
        this.fundingEventLifecycleService = fundingEventLifecycleService;
    }

    @Transactional
    public Page<FundingEvent> listFundingEvents(
        FundingEventStatus status,
        String venue,
        String symbol,
        String sourceType,
        Long candidateId,
        Pageable pageable
    )
    {
        fundingEventLifecycleService.expirePastEvents();
        return fundingEventRepository.findAll( specification( status, venue, symbol, sourceType, candidateId ), pageable )
                                     .map( entity -> FundingEventMapper.toDomain( entity, resolveArmedTradeId( entity.getId() ) ) );
    }

    @Transactional
    public FundingEvent getFundingEvent( Long id )
    {
        fundingEventLifecycleService.expirePastEvents();
        FundingEventEntity entity = fundingEventRepository.findById( id )
                                                          .orElseThrow( () -> new ResourceNotFoundException( "Событие фандинга не найдено: " + id ) );
        return FundingEventMapper.toDomain( entity, resolveArmedTradeId( id ) );
    }

    public Long resolveBaselineLiquidityAssessmentId( Long signalCandidateId )
    {
        if( signalCandidateId == null )
        {
            return null;
        }
        return liquidityAssessmentRepository
            .findFirstBySignalCandidateIdOrderBySampledAtAsc( signalCandidateId )
            .map( a -> a.getId() )
            .orElse( null );
    }

    private Long resolveArmedTradeId( Long fundingEventId )
    {
        return armedTradeRepository.findAllByFundingEventIdOrderByCreatedAtDesc( fundingEventId )
                                   .stream()
                                   .findFirst()
                                   .map( trade -> trade.getId() )
                                   .orElse( null );
    }

    private Specification<FundingEventEntity> specification(
        FundingEventStatus status,
        String venue,
        String symbol,
        String sourceType,
        Long candidateId
    )
    {
        return ( root, query, cb ) -> {
            List<Predicate> predicates = new ArrayList<>();
            if( status != null )
            {
                predicates.add( cb.equal( root.get( "status" ), status ) );
            }
            else
            {
                predicates.add( root.get( "status" ).in( FundingEventStatus.DISCOVERED, FundingEventStatus.ARMED ) );
            }
            if( venue != null && !venue.isBlank() )
            {
                predicates.add( cb.equal( root.get( "venue" ), venue.trim().toLowerCase( Locale.ROOT ) ) );
            }
            if( symbol != null && !symbol.isBlank() )
            {
                predicates.add( cb.equal( root.get( "symbol" ), symbol.trim().toUpperCase( Locale.ROOT ) ) );
            }
            if( sourceType != null && !sourceType.isBlank() )
            {
                predicates.add( cb.equal( root.get( "sourceType" ), sourceType.trim() ) );
            }
            if( candidateId != null )
            {
                predicates.add( cb.equal( root.get( "signalCandidateId" ), candidateId ) );
            }
            query.orderBy( cb.asc( root.get( "fundingTime" ) ), cb.desc( root.get( "discoveredAt" ) ) );
            return cb.and( predicates.toArray( Predicate[]::new ) );
        };
    }
}
