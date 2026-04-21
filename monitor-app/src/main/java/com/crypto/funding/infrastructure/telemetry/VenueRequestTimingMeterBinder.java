package com.crypto.funding.infrastructure.telemetry;

import com.crypto.funding.config.MetadataSyncProperties;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class VenueRequestTimingMeterBinder implements MeterBinder
{
    private static final List<String> OPERATIONS = List.of(
        "metadata-sync",
        "credential-check",
        "uainvest-funding-fetch"
    );

    private final VenueRequestTimingService timingService;
    private final MetadataSyncProperties metadataSyncProperties;
    private final Set<TimingKey> timingKeys;

    public VenueRequestTimingMeterBinder( VenueRequestTimingService timingService, MetadataSyncProperties metadataSyncProperties )
    {
        this.timingService = timingService;
        this.metadataSyncProperties = metadataSyncProperties;
        this.timingKeys = buildKeys();
    }

    @Override
    public void bindTo( MeterRegistry registry )
    {
        for( TimingKey key : timingKeys )
        {
            FunctionCounter.builder( "funding_venue_requests", key, holder -> snapshot( holder ).requests() )
                           .description( "Total venue-facing requests captured by monitor telemetry." )
                           .tags( "venue", key.venue(), "operation", key.operation() )
                           .register( registry );

            FunctionCounter.builder( "funding_venue_request_successes", key, holder -> snapshot( holder ).successes() )
                           .description( "Successful venue-facing requests captured by monitor telemetry." )
                           .tags( "venue", key.venue(), "operation", key.operation() )
                           .register( registry );

            FunctionCounter.builder( "funding_venue_request_failures", key, holder -> snapshot( holder ).failures() )
                           .description( "Failed venue-facing requests captured by monitor telemetry." )
                           .tags( "venue", key.venue(), "operation", key.operation() )
                           .register( registry );

            Gauge.builder( "funding_venue_request_avg_duration_ms", key, holder -> snapshot( holder ).averageDurationMs() )
                 .description( "Average duration of venue-facing requests in milliseconds." )
                 .tags( "venue", key.venue(), "operation", key.operation() )
                 .register( registry );

            Gauge.builder( "funding_venue_request_last_duration_ms", key, holder -> snapshot( holder ).lastDurationMs() )
                 .description( "Last observed venue-facing request duration in milliseconds." )
                 .tags( "venue", key.venue(), "operation", key.operation() )
                 .register( registry );

            Gauge.builder( "funding_venue_request_success_ratio", key, this::successRatio )
                 .description( "Success ratio of venue-facing requests." )
                 .tags( "venue", key.venue(), "operation", key.operation() )
                 .register( registry );

            Gauge.builder( "funding_venue_request_last_payload_size", key, holder -> snapshot( holder ).lastPayloadSize() )
                 .description( "Last observed payload size for venue-facing request telemetry." )
                 .tags( "venue", key.venue(), "operation", key.operation() )
                 .register( registry );

            Gauge.builder( "funding_venue_request_last_http_status", key, this::lastHttpStatus )
                 .description( "Last observed HTTP status code for venue-facing request telemetry." )
                 .tags( "venue", key.venue(), "operation", key.operation() )
                 .register( registry );

            Gauge.builder( "funding_venue_request_last_occurred_epoch_seconds", key, this::lastOccurredEpochSeconds )
                 .description( "Unix epoch seconds for the latest venue-facing request telemetry sample." )
                 .tags( "venue", key.venue(), "operation", key.operation() )
                 .register( registry );
        }
    }

    private Set<TimingKey> buildKeys()
    {
        Set<TimingKey> keys = new LinkedHashSet<>();
        metadataSyncProperties.getEnabledVenues()
                              .stream()
                              .map( venue -> venue.trim().toLowerCase( Locale.ROOT ) )
                              .distinct()
                              .forEach( venue -> OPERATIONS.forEach( operation -> keys.add( new TimingKey( venue, operation ) ) ) );
        keys.add( new TimingKey( "candidate-source", "uainvest-funding-fetch" ) );
        return keys;
    }

    private VenueRequestTimingService.Snapshot snapshot( TimingKey key )
    {
        return timingService.snapshot( key.venue(), key.operation() );
    }

    private double successRatio( TimingKey key )
    {
        VenueRequestTimingService.Snapshot snapshot = snapshot( key );
        return snapshot.requests() == 0 ? 0D : snapshot.successes() / (double) snapshot.requests();
    }

    private double lastOccurredEpochSeconds( TimingKey key )
    {
        Instant lastOccurredAt = snapshot( key ).lastOccurredAt();
        return lastOccurredAt == null ? 0D : lastOccurredAt.getEpochSecond();
    }

    private double lastHttpStatus( TimingKey key )
    {
        Integer status = snapshot( key ).lastHttpStatus();
        return status == null ? 0D : status;
    }

    private record TimingKey( String venue, String operation )
    {
    }
}
