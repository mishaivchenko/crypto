package com.crypto.funding.infrastructure.telemetry;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class VenueRequestTimingService
{
    public record Snapshot(
        String venue,
        String operation,
        long requests,
        long successes,
        long failures,
        long averageDurationMs,
        long lastDurationMs,
        Integer lastHttpStatus,
        String lastError,
        Instant lastOccurredAt,
        long lastPayloadSize
    )
    {
    }

    private final Map<String, Stats> statsByKey = new ConcurrentHashMap<>();

    public void recordSuccess( String venue, String operation, long durationNanos, long payloadSize, Integer httpStatus )
    {
        stats( venue, operation ).recordSuccess( durationNanos, payloadSize, httpStatus );
    }

    public void recordFailure( String venue, String operation, long durationNanos, String error )
    {
        stats( venue, operation ).recordFailure( durationNanos, error );
    }

    public List<Snapshot> snapshots()
    {
        return statsByKey.entrySet()
                         .stream()
                         .map( entry -> entry.getValue().snapshot( keyVenue( entry.getKey() ), keyOperation( entry.getKey() ) ) )
                         .sorted( Comparator.comparing( Snapshot::venue ).thenComparing( Snapshot::operation ) )
                         .toList();
    }

    public List<Snapshot> snapshots( String venue )
    {
        if( venue == null || venue.isBlank() )
        {
            return List.of();
        }

        String normalizedVenue = venue.trim().toLowerCase( Locale.ROOT );
        return snapshots().stream().filter( snapshot -> snapshot.venue().equals( normalizedVenue ) ).toList();
    }

    public void clear()
    {
        statsByKey.clear();
    }

    private Stats stats( String venue, String operation )
    {
        String normalizedVenue = venue == null ? "" : venue.trim().toLowerCase( Locale.ROOT );
        String normalizedOperation = operation == null ? "" : operation.trim().toLowerCase( Locale.ROOT );
        return statsByKey.computeIfAbsent( normalizedVenue + "::" + normalizedOperation, key -> new Stats() );
    }

    private static String keyVenue( String key )
    {
        return key.substring( 0, key.indexOf( "::" ) );
    }

    private static String keyOperation( String key )
    {
        return key.substring( key.indexOf( "::" ) + 2 );
    }

    private static final class Stats
    {
        private final AtomicLong requests = new AtomicLong();
        private final AtomicLong successes = new AtomicLong();
        private final AtomicLong failures = new AtomicLong();
        private final AtomicLong totalDurationMs = new AtomicLong();
        private volatile long lastDurationMs;
        private volatile Integer lastHttpStatus;
        private volatile String lastError;
        private volatile Instant lastOccurredAt;
        private volatile long lastPayloadSize;

        void recordSuccess( long durationNanos, long payloadSize, Integer httpStatus )
        {
            long durationMs = durationNanos / 1_000_000L;
            requests.incrementAndGet();
            successes.incrementAndGet();
            totalDurationMs.addAndGet( durationMs );
            lastDurationMs = durationMs;
            lastHttpStatus = httpStatus;
            lastError = null;
            lastOccurredAt = Instant.now();
            lastPayloadSize = payloadSize;
        }

        void recordFailure( long durationNanos, String error )
        {
            long durationMs = durationNanos / 1_000_000L;
            requests.incrementAndGet();
            failures.incrementAndGet();
            totalDurationMs.addAndGet( durationMs );
            lastDurationMs = durationMs;
            lastHttpStatus = null;
            lastError = error;
            lastOccurredAt = Instant.now();
            lastPayloadSize = 0L;
        }

        Snapshot snapshot( String venue, String operation )
        {
            long requestCount = requests.get();
            long avg = requestCount == 0 ? 0L : totalDurationMs.get() / requestCount;
            return new Snapshot(
                venue,
                operation,
                requestCount,
                successes.get(),
                failures.get(),
                avg,
                lastDurationMs,
                lastHttpStatus,
                lastError,
                lastOccurredAt,
                lastPayloadSize
            );
        }
    }
}
