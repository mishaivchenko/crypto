package com.crypto.funding.infrastructure.telemetry;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class VenueRequestTimingService
{
    static final int ROLLING_WINDOW_SIZE = 20;

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
        long lastPayloadSize,
        Long p50DurationMs,
        Long p95DurationMs,
        Long p99DurationMs
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

    public Snapshot snapshot( String venue, String operation )
    {
        String normalizedVenue = venue == null ? "" : venue.trim().toLowerCase( Locale.ROOT );
        String normalizedOperation = operation == null ? "" : operation.trim().toLowerCase( Locale.ROOT );
        Stats stats = statsByKey.get( normalizedVenue + "::" + normalizedOperation );
        if( stats == null )
        {
            return new Snapshot(
                normalizedVenue,
                normalizedOperation,
                0L,
                0L,
                0L,
                0L,
                0L,
                null,
                null,
                null,
                0L,
                null,
                null,
                null
            );
        }
        return stats.snapshot( normalizedVenue, normalizedOperation );
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
        private final Deque<Long> rollingWindow = new ArrayDeque<>( ROLLING_WINDOW_SIZE );

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
            addToWindow( durationMs );
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
            addToWindow( durationMs );
        }

        private synchronized void addToWindow( long durationMs )
        {
            if( rollingWindow.size() >= ROLLING_WINDOW_SIZE )
            {
                rollingWindow.pollFirst();
            }
            rollingWindow.addLast( durationMs );
        }

        Snapshot snapshot( String venue, String operation )
        {
            long requestCount = requests.get();
            long avg = requestCount == 0 ? 0L : totalDurationMs.get() / requestCount;
            List<Long> sorted;
            synchronized( this )
            {
                sorted = new ArrayList<>( rollingWindow );
            }
            sorted.sort( Comparator.naturalOrder() );
            Long p50 = sorted.isEmpty() ? null : percentile( sorted, 50 );
            Long p95 = sorted.isEmpty() ? null : percentile( sorted, 95 );
            Long p99 = sorted.isEmpty() ? null : percentile( sorted, 99 );
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
                lastPayloadSize,
                p50,
                p95,
                p99
            );
        }

        static long percentile( List<Long> sorted, int pct )
        {
            int idx = (int) Math.max( 0, Math.ceil( pct / 100.0 * sorted.size() ) - 1 );
            return sorted.get( idx );
        }
    }
}
