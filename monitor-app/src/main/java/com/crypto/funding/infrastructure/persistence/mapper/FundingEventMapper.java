package com.crypto.funding.infrastructure.persistence.mapper;

import com.crypto.funding.domain.event.FundingEvent;
import com.crypto.funding.infrastructure.persistence.model.FundingEventEntity;

public final class FundingEventMapper
{
    private FundingEventMapper()
    {
    }

    public static FundingEvent toDomain( FundingEventEntity entity )
    {
        return toDomain( entity, null );
    }

    public static FundingEvent toDomain( FundingEventEntity entity, Long armedTradeId )
    {
        return new FundingEvent(
            entity.getId(),
            entity.getVenue(),
            entity.getSymbol(),
            entity.getFundingTime(),
            entity.getFundingRatePct(),
            entity.getStatus(),
            entity.getSourceType(),
            entity.getSourceRef(),
            entity.getSignalCandidateId(),
            armedTradeId,
            entity.getDiscoveredAt(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
