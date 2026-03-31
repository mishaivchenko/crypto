package com.crypto.funding.application.candidate;

import com.crypto.funding.application.ResourceNotFoundException;
import com.crypto.funding.domain.candidate.SignalCandidate;
import com.crypto.funding.domain.candidate.SignalCandidateStatus;
import com.crypto.funding.infrastructure.persistence.mapper.SignalCandidateMapper;
import com.crypto.funding.infrastructure.persistence.model.SignalCandidateEntity;
import com.crypto.funding.infrastructure.persistence.repository.SignalCandidateJpaRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class SignalCandidateQueryService
{
    private final SignalCandidateJpaRepository candidateRepository;

    public SignalCandidateQueryService( SignalCandidateJpaRepository candidateRepository )
    {
        this.candidateRepository = candidateRepository;
    }

    @Transactional(readOnly = true)
    public Page<SignalCandidate> listCandidates(
        SignalCandidateStatus status,
        String venue,
        String symbol,
        Instant detectedFrom,
        Instant detectedTo,
        Pageable pageable
    )
    {
        return candidateRepository.findAll( specification( status, venue, symbol, detectedFrom, detectedTo ), pageable )
                                  .map( SignalCandidateMapper::toDomain );
    }

    @Transactional(readOnly = true)
    public SignalCandidate getCandidate( Long id )
    {
        return candidateRepository.findById( id )
                                   .map( SignalCandidateMapper::toDomain )
                                   .orElseThrow( () -> new ResourceNotFoundException( "SignalCandidate not found: " + id ) );
    }

    private Specification<SignalCandidateEntity> specification(
        SignalCandidateStatus status,
        String venue,
        String symbol,
        Instant detectedFrom,
        Instant detectedTo
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
                predicates.add( cb.like( root.get( "venueHintsCsv" ), "%" + venue.trim().toLowerCase( Locale.ROOT ) + "%" ) );
            }
            if( symbol != null && !symbol.isBlank() )
            {
                predicates.add( cb.equal( root.get( "normalizedSymbol" ), symbol.trim().toUpperCase( Locale.ROOT ) ) );
            }
            if( detectedFrom != null )
            {
                predicates.add( cb.greaterThanOrEqualTo( root.get( "detectedAt" ), detectedFrom ) );
            }
            if( detectedTo != null )
            {
                predicates.add( cb.lessThanOrEqualTo( root.get( "detectedAt" ), detectedTo ) );
            }
            query.orderBy( cb.desc( root.get( "detectedAt" ) ) );
            return cb.and( predicates.toArray( Predicate[]::new ) );
        };
    }
}
