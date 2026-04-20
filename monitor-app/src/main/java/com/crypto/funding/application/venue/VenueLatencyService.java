package com.crypto.funding.application.venue;

import com.crypto.funding.infrastructure.telemetry.VenueRequestTimingService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class VenueLatencyService
{
    private static final List<String> ENTRY_LATENCY_OPERATIONS = List.of(
        "order-submit",
        "credential-check",
        "metadata-sync"
    );

    private final VenueRequestTimingService timingService;

    public VenueLatencyService( VenueRequestTimingService timingService )
    {
        this.timingService = timingService;
    }

    public Long estimateEntryLatencyMs( String venue )
    {
        if( venue == null || venue.isBlank() )
        {
            return null;
        }

        List<VenueRequestTimingService.Snapshot> snapshots = timingService.snapshots( venue.trim().toLowerCase( Locale.ROOT ) );
        for( String operation : ENTRY_LATENCY_OPERATIONS )
        {
            Long estimate = snapshots.stream()
                                     .filter( snapshot -> operation.equals( snapshot.operation() ) )
                                     .filter( snapshot -> snapshot.successes() > 0 )
                                     .map( VenueRequestTimingService.Snapshot::averageDurationMs )
                                     .findFirst()
                                     .orElse( null );
            if( estimate != null )
            {
                return estimate;
            }
        }
        return null;
    }

    public long effectiveEntryLatencyMs( Long measuredEntryLatencyMs, Long manualAdjustmentMs )
    {
        long measured = measuredEntryLatencyMs == null ? 0L : measuredEntryLatencyMs;
        long manual = manualAdjustmentMs == null ? 0L : manualAdjustmentMs;
        return Math.max( 0L, measured + manual );
    }
}
