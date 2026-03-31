package com.crypto.funding.api;

import com.crypto.funding.api.dto.InstrumentMetadataResponse;
import com.crypto.funding.api.dto.VenueRequestTimingResponse;
import com.crypto.funding.api.dto.VenueSummaryResponse;
import com.crypto.funding.application.venue.VenueDiagnosticsService;
import com.crypto.funding.domain.venue.InstrumentMetadata;
import com.crypto.funding.infrastructure.telemetry.VenueRequestTimingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/venues")
public class VenueDiagnosticsController
{
    private final VenueDiagnosticsService venueDiagnosticsService;

    public VenueDiagnosticsController( VenueDiagnosticsService venueDiagnosticsService )
    {
        this.venueDiagnosticsService = venueDiagnosticsService;
    }

    @GetMapping
    public List<VenueSummaryResponse> list()
    {
        return venueDiagnosticsService.listVenues().stream().map( this::toResponse ).toList();
    }

    @GetMapping("/{venue}")
    public VenueSummaryResponse get( @PathVariable String venue )
    {
        return toResponse( venueDiagnosticsService.getVenue( venue ) );
    }

    @PostMapping("/{venue}/sync")
    public VenueSummaryResponse sync( @PathVariable String venue )
    {
        return toResponse( venueDiagnosticsService.syncVenue( venue ) );
    }

    @GetMapping("/{venue}/instruments")
    public List<InstrumentMetadataResponse> listInstruments(
        @PathVariable String venue,
        @RequestParam(defaultValue = "true") boolean activeOnly
    )
    {
        return venueDiagnosticsService.listInstruments( venue, activeOnly ).stream().map( this::toResponse ).toList();
    }

    @GetMapping("/timings")
    public List<VenueRequestTimingResponse> timings( @RequestParam(required = false) String venue )
    {
        List<VenueRequestTimingService.Snapshot> snapshots = venue == null || venue.isBlank()
                                                             ? venueDiagnosticsService.listTimings()
                                                             : venueDiagnosticsService.listTimings( venue );
        return snapshots.stream().map( this::toResponse ).toList();
    }

    private VenueSummaryResponse toResponse( VenueDiagnosticsService.VenueSummary summary )
    {
        return new VenueSummaryResponse(
            summary.venue(),
            summary.configuredMode(),
            summary.metadataBaseUrl(),
            summary.contractsBaseUrl(),
            summary.credentialsConfigured(),
            summary.enabledForMetadata(),
            summary.metadataProviderAvailable(),
            summary.activeInstrumentCount(),
            summary.lastSyncedAt()
        );
    }

    private InstrumentMetadataResponse toResponse( InstrumentMetadata instrument )
    {
        return new InstrumentMetadataResponse(
            instrument.id(),
            instrument.venue(),
            instrument.canonicalSymbol(),
            instrument.venueSymbol(),
            instrument.baseAsset(),
            instrument.quoteAsset(),
            instrument.instrumentType(),
            instrument.status(),
            instrument.minOrderQty(),
            instrument.qtyStep(),
            instrument.minNotionalValue(),
            instrument.quantityPrecision(),
            instrument.lastSyncedAt()
        );
    }

    private VenueRequestTimingResponse toResponse( VenueRequestTimingService.Snapshot snapshot )
    {
        return new VenueRequestTimingResponse(
            snapshot.venue(),
            snapshot.operation(),
            snapshot.requests(),
            snapshot.successes(),
            snapshot.failures(),
            snapshot.averageDurationMs(),
            snapshot.lastDurationMs(),
            snapshot.lastHttpStatus(),
            snapshot.lastError(),
            snapshot.lastOccurredAt(),
            snapshot.lastPayloadSize()
        );
    }
}
