package com.crypto.funding.application.execution;

import com.crypto.funding.contract.engine.EngineLatencySampleRequest;
import com.crypto.funding.infrastructure.persistence.model.VenueTimingProfileEntity;
import com.crypto.funding.infrastructure.persistence.repository.VenueTimingProfileJpaRepository;
import com.crypto.funding.infrastructure.telemetry.VenueRequestTimingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EngineLatencyRecordService
{
    private final VenueTimingProfileJpaRepository timingProfileRepository;
    private final VenueRequestTimingService timingService;

    public EngineLatencyRecordService(
        VenueTimingProfileJpaRepository timingProfileRepository,
        VenueRequestTimingService timingService
    )
    {
        this.timingProfileRepository = timingProfileRepository;
        this.timingService = timingService;
    }

    @Transactional
    public void record( EngineLatencySampleRequest request )
    {
        String symbol = request.symbol() == null || request.symbol().isBlank() ? "_all_" : request.symbol();
        VenueTimingProfileEntity timing = timingProfileRepository
            .findFirstByVenueAndSymbolOrderBySampledAtDesc( request.venue(), symbol )
            .orElseGet( () -> {
                VenueTimingProfileEntity entity = new VenueTimingProfileEntity();
                entity.setVenue( request.venue() );
                entity.setSymbol( symbol );
                return entity;
            } );

        timing.setEntryLatencyMs( request.durationMs() );
        timing.setSampledAt( request.sampledAt() );
        timingProfileRepository.save( timing );

        timingService.recordSuccess( request.venue(), request.operation(), request.durationMs() * 1_000_000L, 0, null );
    }
}
