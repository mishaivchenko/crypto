package com.crypto.funding.config;

import com.crypto.funding.legacy.execution.LegacyExecutionGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ExecutionSafetyDiagnostics implements ApplicationRunner
{
    private static final Logger log = LoggerFactory.getLogger( ExecutionSafetyDiagnostics.class );

    private final LegacyExecutionGuard guard;

    public ExecutionSafetyDiagnostics( LegacyExecutionGuard guard )
    {
        this.guard = guard;
    }

    @Override
    public void run( ApplicationArguments args )
    {
        log.info(
            "[execution-safety] mode={} legacyEnabled={} liveVenues={} blockedVenues={}",
            guard.mode(),
            guard.isLegacyEnabled(),
            guard.liveVenues(),
            guard.blockedVenues()
        );
    }
}
