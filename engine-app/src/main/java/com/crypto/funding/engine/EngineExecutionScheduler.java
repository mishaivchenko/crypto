package com.crypto.funding.engine;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EngineExecutionScheduler
{
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
        if( runtimeControlService.shouldRunScheduledLoop() )
        {
            try
            {
                // Stabilization delay — prevents thundering-herd on startup
                Thread.sleep( 500 );
            }
            catch( InterruptedException e )
            {
                Thread.currentThread().interrupt();
            }
            executionService.runOnce( false );
        }
    }
}
