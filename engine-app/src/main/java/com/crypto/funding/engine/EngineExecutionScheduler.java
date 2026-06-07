package com.crypto.funding.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EngineExecutionScheduler
{
    private static final Logger log = LoggerFactory.getLogger( EngineExecutionScheduler.class );

    private final EngineRuntimeControlService runtimeControlService;
    private final EngineExecutionService executionService;

    public EngineExecutionScheduler( EngineRuntimeControlService runtimeControlService, EngineExecutionService executionService )
    {
        this.runtimeControlService = runtimeControlService;
        this.executionService = executionService;
    }

    @Scheduled(fixedDelayString = "${engine.execution-scheduler-tick-ms:250}")
    public void runLoop()
    {
        if( !runtimeControlService.shouldRunScheduledLoop() )
        {
            return;
        }
        try
        {
            executionService.runOnce( false );
        }
        catch( Exception e )
        {
            log.warn( "Engine execution loop error (will retry next tick): {}", e.getMessage() );
        }
    }
}
