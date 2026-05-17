package com.crypto.funding.api;

import com.crypto.funding.api.dto.GlobalVenueModeResponse;
import com.crypto.funding.api.dto.InstrumentMetadataResponse;
import com.crypto.funding.api.dto.SetGlobalVenueModeRequest;
import com.crypto.funding.api.dto.SetVenueDefaultLatencyRequest;
import com.crypto.funding.api.dto.SetVenueModeRequest;
import com.crypto.funding.api.dto.VenueLatencyProbeResponse;
import com.crypto.funding.api.dto.VenueRequestTimingResponse;
import com.crypto.funding.api.dto.VenueSummaryResponse;
import com.crypto.funding.application.venue.VenueDiagnosticsService;
import com.crypto.funding.application.venue.VenueLatencyProbeService;
import com.crypto.funding.application.venue.VenueProfileService;
import com.crypto.funding.domain.venue.InstrumentMetadata;
import com.crypto.funding.infrastructure.telemetry.VenueRequestTimingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/venues")
public class VenueDiagnosticsController
{
    private final VenueDiagnosticsService venueDiagnosticsService;
    private final VenueLatencyProbeService latencyProbeService;
    private final VenueProfileService venueProfileService;

    public VenueDiagnosticsController(
        VenueDiagnosticsService venueDiagnosticsService,
        VenueLatencyProbeService latencyProbeService,
        VenueProfileService venueProfileService
    )
    {
        this.venueDiagnosticsService = venueDiagnosticsService;
        this.latencyProbeService = latencyProbeService;
        this.venueProfileService = venueProfileService;
    }

    @GetMapping
    public List<VenueSummaryResponse> list()
    {
        return venueDiagnosticsService.listVenues().stream().map( this::toResponse ).toList();
    }

    @GetMapping("/access-mode")
    public GlobalVenueModeResponse getGlobalMode()
    {
        return toResponse( venueDiagnosticsService.getGlobalMode() );
    }

    @PostMapping("/access-mode")
    public GlobalVenueModeResponse setGlobalMode( @Valid @RequestBody SetGlobalVenueModeRequest request )
    {
        return toResponse( venueDiagnosticsService.setGlobalMode( request.mode() ) );
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

    @PostMapping("/{venue}/mode")
    public VenueSummaryResponse setMode( @PathVariable String venue, @Valid @RequestBody SetVenueModeRequest request )
    {
        return toResponse( venueDiagnosticsService.setMode( venue, request.mode() ) );
    }

    @PostMapping("/{venue}/check")
    public VenueSummaryResponse checkCredentials( @PathVariable String venue )
    {
        return toResponse( venueDiagnosticsService.checkCredentials( venue ) );
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

    @PostMapping("/{venue}/latency-probe")
    public VenueLatencyProbeResponse probeLatency( @PathVariable String venue )
    {
        VenueLatencyProbeService.ProbeResult result = latencyProbeService.probe( venue );
        return new VenueLatencyProbeResponse( result.venue(), result.durationMs(), result.sampledAt() );
    }

    @PostMapping("/{venue}/default-latency")
    public VenueDefaultLatencyResponse setDefaultLatency(
        @PathVariable String venue,
        @RequestBody SetVenueDefaultLatencyRequest request
    )
    {
        VenueProfileService.VenueAccessProfile profile = venueProfileService.updateDefaultLatency(
            venue, request.defaultManualLatencyAdjustmentMs()
        );
        return new VenueDefaultLatencyResponse( profile.venue(), profile.defaultManualLatencyAdjustmentMs() );
    }

    public record VenueDefaultLatencyResponse( String venue, Long defaultManualLatencyAdjustmentMs )
    {
    }

    private VenueSummaryResponse toResponse( VenueDiagnosticsService.VenueSummary summary )
    {
        return new VenueSummaryResponse(
            summary.venue(),
            summary.configuredMode(),
            summary.metadataBaseUrl(),
            summary.contractsBaseUrl(),
            summary.credentialsConfigured(),
            summary.apiKeyLoaded(),
            summary.secretKeyLoaded(),
            summary.passphraseLoaded(),
            summary.credentialsRequired(),
            summary.modeOverridden(),
            summary.availableModes(),
            summary.connectionStatus(),
            summary.connectionMessage(),
            summary.lastConnectionHttpStatus(),
            summary.lastCheckedAt(),
            summary.enabledForMetadata(),
            summary.metadataProviderAvailable(),
            summary.activeInstrumentCount(),
            summary.lastSyncedAt()
        );
    }

    private GlobalVenueModeResponse toResponse( com.crypto.funding.application.venue.VenueProfileService.GlobalAccessProfile profile )
    {
        return new GlobalVenueModeResponse(
            profile.mode(),
            profile.modeOverridden(),
            profile.availableModes()
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
            snapshot.lastPayloadSize(),
            snapshot.p50DurationMs(),
            snapshot.p95DurationMs(),
            snapshot.p99DurationMs()
        );
    }
}
