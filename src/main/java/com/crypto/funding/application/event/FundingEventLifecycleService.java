package com.crypto.funding.application.event;

import com.crypto.funding.domain.event.FundingEventStatus;
import com.crypto.funding.infrastructure.persistence.model.FundingEventEntity;
import com.crypto.funding.infrastructure.persistence.repository.FundingEventJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;

@Service
public class FundingEventLifecycleService
{
    private static final Set<FundingEventStatus> EXPIRABLE_STATUSES = Set.of(
        FundingEventStatus.DISCOVERED,
        FundingEventStatus.ARMED
    );

    private final FundingEventJpaRepository fundingEventRepository;
    private final Clock clock;

    @Autowired
    public FundingEventLifecycleService( FundingEventJpaRepository fundingEventRepository )
    {
        this( fundingEventRepository, Clock.systemUTC() );
    }

    FundingEventLifecycleService( FundingEventJpaRepository fundingEventRepository, Clock clock )
    {
        this.fundingEventRepository = fundingEventRepository;
        this.clock = clock;
    }

    @Transactional
    public int expirePastEvents()
    {
        Instant now = Instant.now( clock );
        int expired = 0;
        for( FundingEventEntity entity : fundingEventRepository.findAllByStatusInAndFundingTimeLessThanEqual( EXPIRABLE_STATUSES, now ) )
        {
            if( entity.getStatus() != FundingEventStatus.EXPIRED )
            {
                entity.setStatus( FundingEventStatus.EXPIRED );
                fundingEventRepository.save( entity );
                expired++;
            }
        }
        return expired;
    }
}
