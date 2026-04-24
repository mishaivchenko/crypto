package com.crypto.funding.engine;

import com.crypto.funding.domain.execution.OrderAttemptStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EngineTelemetryServiceTest
{
    // REQ: ENG-TEL-001
    @Test
    void keepsAveragesZeroSafeBeforeAnyEvents()
    {
        var snapshot = new EngineTelemetryService().snapshot();

        assertThat( snapshot.averageExecutionRunDurationMs() ).isZero();
        assertThat( snapshot.averagePlanFetchDurationMs() ).isZero();
        assertThat( snapshot.averageAttemptRecordDurationMs() ).isZero();
        assertThat( snapshot.attemptStatusBreakdown().values() ).allMatch( count -> count == 0L );
    }

    // REQ: ENG-TEL-002
    // REQ: ENG-TEL-003
    @Test
    void aggregatesPerVenueDurationsAndStableFailureBuckets()
    {
        EngineTelemetryService service = new EngineTelemetryService();

        service.recordOrderSubmission( " Bybit ", OrderAttemptStatus.FAILED, 40 );
        service.recordOrderSubmission( "BYBIT", OrderAttemptStatus.SUBMITTED, 50 );
        service.recordOrderSubmission( " ", OrderAttemptStatus.EXPIRED, -5 );
        service.recordOrderSubmission( "gate", OrderAttemptStatus.CANCELLED, 70 );
        service.recordOrderSubmission( "gate", OrderAttemptStatus.ACKNOWLEDGED, 30 );

        var snapshot = service.snapshot();

        assertThat( snapshot.attemptStatusBreakdown() )
            .containsEntry( "failed", 1L )
            .containsEntry( "submitted", 1L )
            .containsEntry( "expired", 1L )
            .containsEntry( "cancelled", 1L )
            .containsEntry( "acknowledged", 1L );
        assertThat( snapshot.attemptVenueBreakdown() )
            .containsEntry( "bybit", 2L )
            .containsEntry( "gate", 2L )
            .containsEntry( "unknown", 1L );
        assertThat( snapshot.failedAttemptVenueBreakdown() )
            .containsEntry( "bybit", 1L )
            .containsEntry( "gate", 1L )
            .containsEntry( "unknown", 1L );
        assertThat( snapshot.averageSubmitDurationMsByVenue() )
            .containsEntry( "bybit", 45L )
            .containsEntry( "gate", 50L )
            .containsEntry( "unknown", 0L );
        assertThat( snapshot.lastSubmitDurationMsByVenue() )
            .containsEntry( "bybit", 50L )
            .containsEntry( "gate", 30L )
            .containsEntry( "unknown", 0L );
    }

    // REQ: ENG-TEL-004
    // REQ: ENG-TEL-001
    @Test
    void tracksForcedRunStateSeparatelyFromScheduledRuns()
    {
        EngineTelemetryService service = new EngineTelemetryService();

        service.recordExecutionRun(
            false,
            Instant.parse( "2030-01-01T00:00:00Z" ),
            Instant.parse( "2030-01-01T00:00:01Z" ),
            5,
            2,
            3,
            120
        );
        service.recordExecutionRun(
            true,
            Instant.parse( "2030-01-01T00:05:00Z" ),
            Instant.parse( "2030-01-01T00:05:01Z" ),
            8,
            4,
            4,
            80
        );
        service.recordPlanFetch( 40 );
        service.recordPlanFetch( 20 );
        service.recordAttemptRecord( 30 );

        var snapshot = service.snapshot();

        assertThat( snapshot.executionRuns() ).isEqualTo( 2 );
        assertThat( snapshot.forcedExecutionRuns() ).isEqualTo( 1 );
        assertThat( snapshot.scheduledExecutionRuns() ).isEqualTo( 1 );
        assertThat( snapshot.averageExecutionRunDurationMs() ).isEqualTo( 100 );
        assertThat( snapshot.lastExecutionRunDurationMs() ).isEqualTo( 80 );
        assertThat( snapshot.lastRunForced() ).isTrue();
        assertThat( snapshot.lastForcedPlansScanned() ).isEqualTo( 8 );
        assertThat( snapshot.lastForcedAttemptsSubmitted() ).isEqualTo( 4 );
        assertThat( snapshot.lastForcedAttemptsSkipped() ).isEqualTo( 4 );
        assertThat( snapshot.lastForcedRunDurationMs() ).isEqualTo( 80 );
        assertThat( snapshot.averagePlanFetchDurationMs() ).isEqualTo( 30 );
        assertThat( snapshot.averageAttemptRecordDurationMs() ).isEqualTo( 30 );
    }

    // REQ: ENG-TEL-001
    // REQ: ENG-TEL-003
    @Test
    void normalizesNegativeDurationsAndIgnoresNullStatusBuckets()
    {
        EngineTelemetryService service = new EngineTelemetryService();

        service.recordExecutionRun(
            false,
            Instant.parse( "2030-01-01T00:00:00Z" ),
            Instant.parse( "2030-01-01T00:00:00Z" ),
            -1,
            -2,
            -3,
            -40
        );
        service.recordPlanFetch( -10 );
        service.recordAttemptRecord( -20 );
        service.recordOrderSubmission( null, null, -30 );

        var snapshot = service.snapshot();

        assertThat( snapshot.lastExecutionRunDurationMs() ).isZero();
        assertThat( snapshot.lastPlansScanned() ).isZero();
        assertThat( snapshot.lastAttemptsSubmitted() ).isZero();
        assertThat( snapshot.lastAttemptsSkipped() ).isZero();
        assertThat( snapshot.lastPlanFetchDurationMs() ).isZero();
        assertThat( snapshot.lastAttemptRecordDurationMs() ).isZero();
        assertThat( snapshot.attemptStatusBreakdown().values() ).allMatch( count -> count == 0L );
        assertThat( snapshot.attemptVenueBreakdown() ).containsEntry( "unknown", 1L );
        assertThat( snapshot.lastSubmitDurationMsByVenue() ).containsEntry( "unknown", 0L );
    }
}
