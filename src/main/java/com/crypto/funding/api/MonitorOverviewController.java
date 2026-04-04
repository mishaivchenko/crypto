package com.crypto.funding.api;

import com.crypto.funding.api.dto.MonitorOverviewResponse;
import com.crypto.funding.api.dto.VenueOverviewItemResponse;
import com.crypto.funding.application.monitor.MonitorOverviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v2/monitor")
public class MonitorOverviewController
{
    private final MonitorOverviewService monitorOverviewService;

    public MonitorOverviewController( MonitorOverviewService monitorOverviewService )
    {
        this.monitorOverviewService = monitorOverviewService;
    }

    @GetMapping("/overview")
    public MonitorOverviewResponse overview()
    {
        MonitorOverviewService.Overview overview = monitorOverviewService.load();
        return new MonitorOverviewResponse(
            overview.version(),
            overview.pendingCandidates(),
            overview.fundingEvents(),
            overview.discoveredEvents(),
            overview.armedTrades(),
            overview.activeVenues(),
            overview.venues().stream().map( venue -> new VenueOverviewItemResponse(
                venue.venue(),
                venue.mode(),
                venue.credentialsConfigured(),
                venue.activeInstrumentCount(),
                venue.lastSyncedAt(),
                venue.averageRequestTimeMs(),
                venue.requests()
            ) ).toList(),
            Instant.now()
        );
    }
}
