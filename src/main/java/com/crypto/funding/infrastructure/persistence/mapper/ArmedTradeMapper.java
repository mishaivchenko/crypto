package com.crypto.funding.infrastructure.persistence.mapper;

import com.crypto.funding.domain.trade.ArmedTrade;
import com.crypto.funding.infrastructure.persistence.model.ArmedTradeEntity;

public final class ArmedTradeMapper
{
    private ArmedTradeMapper()
    {
    }

    public static ArmedTrade toDomain( ArmedTradeEntity entity )
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
