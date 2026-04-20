package com.crypto.funding.infrastructure.persistence.mapper;

import com.crypto.funding.domain.venue.InstrumentMetadata;
import com.crypto.funding.infrastructure.persistence.model.InstrumentMetadataEntity;

public final class InstrumentMetadataMapper
{
    private InstrumentMetadataMapper()
    {
    }

    public static InstrumentMetadata toDomain( InstrumentMetadataEntity entity )
    {
        return new InstrumentMetadata(
            entity.getId(),
            entity.getVenue(),
            entity.getCanonicalSymbol(),
            entity.getVenueSymbol(),
            entity.getBaseAsset(),
            entity.getQuoteAsset(),
            entity.getInstrumentType(),
            entity.getStatus(),
            entity.getMinOrderQty(),
            entity.getQtyStep(),
            entity.getMinNotionalValue(),
            entity.getQuantityPrecision(),
            entity.getLastSyncedAt(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
