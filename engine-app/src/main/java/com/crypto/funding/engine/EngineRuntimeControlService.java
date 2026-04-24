package com.crypto.funding.engine;

import com.crypto.funding.contract.engine.EngineRuntimeControlRequest;
import com.crypto.funding.contract.engine.EngineRuntimeControlResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class EngineRuntimeControlService
{
    static final long MIN_EXECUTION_LOOP_INTERVAL_MS = 100L;

    private final AtomicBoolean executionLoopEnabled = new AtomicBoolean();
    private final AtomicLong executionLoopIntervalMs = new AtomicLong();
    private final AtomicLong lastScheduledDispatchAtMs = new AtomicLong( Long.MIN_VALUE );
    private final EngineTelemetryService telemetryService;
    private final Clock clock;

    private volatile Instant updatedAt;

    @Autowired
    public EngineRuntimeControlService( EngineProperties properties, EngineTelemetryService telemetryService )
    {
        this( properties, telemetryService, Clock.systemUTC() );
    }

    EngineRuntimeControlService( EngineProperties properties, EngineTelemetryService telemetryService, Clock clock )
    {
        this.telemetryService = telemetryService;
        this.clock = clock;
        this.executionLoopEnabled.set( properties.isExecutionLoopEnabled() );
        this.executionLoopIntervalMs.set( clampInterval( properties.getExecutionLoopIntervalMs() ) );
        this.updatedAt = Instant.now( clock );
    }

    public EngineRuntimeControlResponse snapshot()
    {
        return toResponse( telemetryService.snapshot() );
    }

    public EngineRuntimeControlResponse update( EngineRuntimeControlRequest request )
    {
        if( request.executionLoopEnabled() != null )
        {
            executionLoopEnabled.set( request.executionLoopEnabled() );
        }
        if( request.executionLoopIntervalMs() != null )
        {
            executionLoopIntervalMs.set( clampInterval( request.executionLoopIntervalMs() ) );
        }
        lastScheduledDispatchAtMs.set( Long.MIN_VALUE );
        updatedAt = Instant.now( clock );
        return snapshot();
    }

    public boolean shouldRunScheduledLoop()
    {
        if( !executionLoopEnabled.get() )
        {
            return false;
        }
        long now = clock.millis();
        long interval = executionLoopIntervalMs.get();
        long previous = lastScheduledDispatchAtMs.get();
        if( previous != Long.MIN_VALUE && now - previous < interval )
        {
            return false;
        }
        return tryMarkScheduledDispatch( previous, now );
    }

    public boolean executionLoopEnabled()
    {
        return executionLoopEnabled.get();
    }

    public long executionLoopIntervalMs()
    {
        return executionLoopIntervalMs.get();
    }

    public Instant updatedAt()
    {
        return updatedAt;
    }

    private EngineRuntimeControlResponse toResponse( EngineTelemetryService.RuntimeSnapshot telemetry )
    {
        return new EngineRuntimeControlResponse(
            "engine-app",
            "2.0.0",
            executionLoopEnabled(),
            executionLoopIntervalMs(),
            MIN_EXECUTION_LOOP_INTERVAL_MS,
            updatedAt(),
            telemetry.lastRunStartedAt(),
            telemetry.lastRunFinishedAt(),
            telemetry.lastRunForced(),
            telemetry.lastPlansScanned(),
            telemetry.lastAttemptsSubmitted(),
            telemetry.lastAttemptsSkipped(),
            telemetry.lastExecutionRunDurationMs(),
            telemetry.lastForcedRunStartedAt(),
            telemetry.lastForcedRunFinishedAt(),
            telemetry.lastForcedPlansScanned(),
            telemetry.lastForcedAttemptsSubmitted(),
            telemetry.lastForcedAttemptsSkipped(),
            telemetry.lastForcedRunDurationMs()
        );
    }

    boolean tryMarkScheduledDispatch( long previous, long now )
    {
        return lastScheduledDispatchAtMs.compareAndSet( previous, now );
    }

    private static long clampInterval( long value )
    {
        return Math.max( MIN_EXECUTION_LOOP_INTERVAL_MS, value );
    }
}
