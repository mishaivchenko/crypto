package com.crypto.funding.application.event;

import com.crypto.funding.application.ResourceNotFoundException;
import com.crypto.funding.domain.event.FundingEvent;
import com.crypto.funding.domain.event.FundingEventStatus;
import com.crypto.funding.infrastructure.persistence.mapper.FundingEventMapper;
import com.crypto.funding.infrastructure.persistence.model.FundingEventEntity;
import com.crypto.funding.infrastructure.persistence.repository.FundingEventJpaRepository;
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

    public FundingEventQueryService( FundingEventJpaRepository fundingEventRepository )
    {
        this.fundingEventRepository = fundingEventRepository;
    }

    @Transactional(readOnly = true)
    public Page<FundingEvent> listFundingEvents(
        FundingEventStatus status,
        String venue,
        String symbol,
        String sourceType,
        Long candidateId,
        Pageable pageable
    )
    {
        return fundingEventRepository.findAll( specification( status, venue, symbol, sourceType, candidateId ), pageable )
                                     .map( FundingEventMapper::toDomain );
    }

    @Transactional(readOnly = true)
    public FundingEvent getFundingEvent( Long id )
    {
        return fundingEventRepository.findById( id )
                                     .map( FundingEventMapper::toDomain )
                                     .orElseThrow( () -> new ResourceNotFoundException( "FundingEvent not found: " + id ) );
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
