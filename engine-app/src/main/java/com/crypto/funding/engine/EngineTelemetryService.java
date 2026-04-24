package com.crypto.funding.engine;

import com.crypto.funding.domain.execution.OrderAttemptStatus;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class EngineTelemetryService
{
    public record RuntimeSnapshot(
        long executionRuns,
        long forcedExecutionRuns,
        long scheduledExecutionRuns,
        long averageExecutionRunDurationMs,
        long lastExecutionRunDurationMs,
        java.time.Instant lastRunStartedAt,
        java.time.Instant lastRunFinishedAt,
        boolean lastRunForced,
        int lastPlansScanned,
        int lastAttemptsSubmitted,
        int lastAttemptsSkipped,
        java.time.Instant lastForcedRunStartedAt,
        java.time.Instant lastForcedRunFinishedAt,
        int lastForcedPlansScanned,
        int lastForcedAttemptsSubmitted,
        int lastForcedAttemptsSkipped,
        long lastForcedRunDurationMs,
        long averagePlanFetchDurationMs,
        long lastPlanFetchDurationMs,
        long averageAttemptRecordDurationMs,
        long lastAttemptRecordDurationMs,
        Map<String, Long> attemptStatusBreakdown,
        Map<String, Long> attemptVenueBreakdown,
        Map<String, Long> failedAttemptVenueBreakdown,
        Map<String, Long> averageSubmitDurationMsByVenue,
        Map<String, Long> lastSubmitDurationMsByVenue
    )
    {
    }

    private final AtomicLong executionRuns = new AtomicLong();
    private final AtomicLong forcedExecutionRuns = new AtomicLong();
    private final AtomicLong scheduledExecutionRuns = new AtomicLong();
    private final AtomicLong totalExecutionRunDurationMs = new AtomicLong();
    private final AtomicLong planFetches = new AtomicLong();
    private final AtomicLong totalPlanFetchDurationMs = new AtomicLong();
    private final AtomicLong attemptRecordRequests = new AtomicLong();
    private final AtomicLong totalAttemptRecordDurationMs = new AtomicLong();

    private volatile long lastExecutionRunDurationMs;
    private volatile java.time.Instant lastRunStartedAt;
    private volatile java.time.Instant lastRunFinishedAt;
    private volatile boolean lastRunForced;
    private volatile int lastPlansScanned;
    private volatile int lastAttemptsSubmitted;
    private volatile int lastAttemptsSkipped;
    private volatile java.time.Instant lastForcedRunStartedAt;
    private volatile java.time.Instant lastForcedRunFinishedAt;
    private volatile int lastForcedPlansScanned;
    private volatile int lastForcedAttemptsSubmitted;
    private volatile int lastForcedAttemptsSkipped;
    private volatile long lastForcedRunDurationMs;
    private volatile long lastPlanFetchDurationMs;
    private volatile long lastAttemptRecordDurationMs;

    private final Map<OrderAttemptStatus, AtomicLong> attemptStatusBreakdown = new ConcurrentHashMap<>();
    private final Map<String, VenueSubmitStats> submitStatsByVenue = new ConcurrentHashMap<>();

    public EngineTelemetryService()
    {
        for( OrderAttemptStatus status : OrderAttemptStatus.values() )
        {
            attemptStatusBreakdown.put( status, new AtomicLong() );
        }
    }

    public void recordExecutionRun(
        boolean force,
        java.time.Instant startedAt,
        java.time.Instant finishedAt,
        int plansScanned,
        int attemptsSubmitted,
        int attemptsSkipped,
        long durationMs
    )
    {
        executionRuns.incrementAndGet();
        if( force )
        {
            forcedExecutionRuns.incrementAndGet();
        }
        else
        {
            scheduledExecutionRuns.incrementAndGet();
        }
        totalExecutionRunDurationMs.addAndGet( Math.max( 0L, durationMs ) );
        lastExecutionRunDurationMs = Math.max( 0L, durationMs );
        lastRunStartedAt = startedAt;
        lastRunFinishedAt = finishedAt;
        lastRunForced = force;
        lastPlansScanned = Math.max( 0, plansScanned );
        lastAttemptsSubmitted = Math.max( 0, attemptsSubmitted );
        lastAttemptsSkipped = Math.max( 0, attemptsSkipped );
        if( force )
        {
            lastForcedRunStartedAt = startedAt;
            lastForcedRunFinishedAt = finishedAt;
            lastForcedPlansScanned = Math.max( 0, plansScanned );
            lastForcedAttemptsSubmitted = Math.max( 0, attemptsSubmitted );
            lastForcedAttemptsSkipped = Math.max( 0, attemptsSkipped );
            lastForcedRunDurationMs = Math.max( 0L, durationMs );
        }
    }

    public void recordPlanFetch( long durationMs )
    {
        planFetches.incrementAndGet();
        totalPlanFetchDurationMs.addAndGet( Math.max( 0L, durationMs ) );
        lastPlanFetchDurationMs = Math.max( 0L, durationMs );
    }

    public void recordAttemptRecord( long durationMs )
    {
        attemptRecordRequests.incrementAndGet();
        totalAttemptRecordDurationMs.addAndGet( Math.max( 0L, durationMs ) );
        lastAttemptRecordDurationMs = Math.max( 0L, durationMs );
    }

    public void recordOrderSubmission( String venue, OrderAttemptStatus status, long durationMs )
    {
        if( status != null )
        {
            AtomicLong counter = attemptStatusBreakdown.get( status );
            if( counter != null )
            {
                counter.incrementAndGet();
            }
        }
        submitStatsByVenue.computeIfAbsent( normalizeVenue( venue ), ignored -> new VenueSubmitStats() )
                         .record( status, durationMs );
    }

    public RuntimeSnapshot snapshot()
    {
        return new RuntimeSnapshot(
            executionRuns.get(),
            forcedExecutionRuns.get(),
            scheduledExecutionRuns.get(),
            average( totalExecutionRunDurationMs.get(), executionRuns.get() ),
            lastExecutionRunDurationMs,
            lastRunStartedAt,
            lastRunFinishedAt,
            lastRunForced,
            lastPlansScanned,
            lastAttemptsSubmitted,
            lastAttemptsSkipped,
            lastForcedRunStartedAt,
            lastForcedRunFinishedAt,
            lastForcedPlansScanned,
            lastForcedAttemptsSubmitted,
            lastForcedAttemptsSkipped,
            lastForcedRunDurationMs,
            average( totalPlanFetchDurationMs.get(), planFetches.get() ),
            lastPlanFetchDurationMs,
            average( totalAttemptRecordDurationMs.get(), attemptRecordRequests.get() ),
            lastAttemptRecordDurationMs,
            attemptStatusSnapshot(),
            attemptVenueSnapshot(),
            failedAttemptVenueSnapshot(),
            averageSubmitDurationSnapshot(),
            lastSubmitDurationSnapshot()
        );
    }

    private Map<String, Long> attemptStatusSnapshot()
    {
        Map<String, Long> snapshot = new TreeMap<>();
        attemptStatusBreakdown.forEach( ( status, count ) -> snapshot.put( status.name().toLowerCase( Locale.ROOT ), count.get() ) );
        return Collections.unmodifiableMap( snapshot );
    }

    private Map<String, Long> attemptVenueSnapshot()
    {
        Map<String, Long> snapshot = new TreeMap<>();
        submitStatsByVenue.forEach( ( venue, stats ) -> snapshot.put( venue, stats.attempts() ) );
        return Collections.unmodifiableMap( snapshot );
    }

    private Map<String, Long> failedAttemptVenueSnapshot()
    {
        Map<String, Long> snapshot = new TreeMap<>();
        submitStatsByVenue.forEach( ( venue, stats ) -> snapshot.put( venue, stats.failedAttempts() ) );
        return Collections.unmodifiableMap( snapshot );
    }

    private Map<String, Long> averageSubmitDurationSnapshot()
    {
        Map<String, Long> snapshot = new TreeMap<>();
        submitStatsByVenue.forEach( ( venue, stats ) -> snapshot.put( venue, stats.averageDurationMs() ) );
        return Collections.unmodifiableMap( snapshot );
    }

    private Map<String, Long> lastSubmitDurationSnapshot()
    {
        Map<String, Long> snapshot = new TreeMap<>();
        submitStatsByVenue.forEach( ( venue, stats ) -> snapshot.put( venue, stats.lastDurationMs() ) );
        return Collections.unmodifiableMap( snapshot );
    }

    private static long average( long total, long count )
    {
        return count <= 0 ? 0L : total / count;
    }

    private static String normalizeVenue( String venue )
    {
        if( venue == null || venue.isBlank() )
        {
            return "unknown";
        }
        return venue.trim().toLowerCase( Locale.ROOT );
    }

    private static final class VenueSubmitStats
    {
        private final AtomicLong attempts = new AtomicLong();
        private final AtomicLong failedAttempts = new AtomicLong();
        private final AtomicLong totalDurationMs = new AtomicLong();
        private volatile long lastDurationMs;

        void record( OrderAttemptStatus status, long durationMs )
        {
            attempts.incrementAndGet();
            if( isFailure( status ) )
            {
                failedAttempts.incrementAndGet();
            }
            long normalizedDuration = Math.max( 0L, durationMs );
            totalDurationMs.addAndGet( normalizedDuration );
            lastDurationMs = normalizedDuration;
        }

        long attempts()
        {
            return attempts.get();
        }

        long failedAttempts()
        {
            return failedAttempts.get();
        }

        long averageDurationMs()
        {
            long attemptCount = attempts.get();
            return attemptCount == 0 ? 0L : totalDurationMs.get() / attemptCount;
        }

        long lastDurationMs()
        {
            return lastDurationMs;
        }

        private static boolean isFailure( OrderAttemptStatus status )
        {
            return status == OrderAttemptStatus.CANCELLED
                   || status == OrderAttemptStatus.REJECTED
                   || status == OrderAttemptStatus.FAILED
                   || status == OrderAttemptStatus.EXPIRED;
        }
    }
}
