package com.crypto.funding.application.event;

import com.crypto.funding.domain.event.FundingEvent;
import com.crypto.funding.domain.event.FundingEventStatus;
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

    public FundingEventCommandService( FundingEventJpaRepository fundingEventRepository )
    {
        this.fundingEventRepository = fundingEventRepository;
    }

    @Transactional
    public FundingEvent create( CreateFundingEventCommand command )
    {
        FundingEventEntity entity = new FundingEventEntity();
        entity.setVenue( normalizeVenue( command.venue() ) );
        entity.setSymbol( normalizeSymbol( command.symbol() ) );
        entity.setFundingTime( command.fundingTime() );
        entity.setFundingRatePct( command.fundingRatePct() );
        entity.setStatus( FundingEventStatus.DISCOVERED );
        entity.setSourceType( normalizeNullable( command.sourceType() ) );
        entity.setSourceRef( normalizeNullable( command.sourceRef() ) );
        entity.setDiscoveredAt( Instant.now() );

        return FundingEventMapper.toDomain( fundingEventRepository.save( entity ) );
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
