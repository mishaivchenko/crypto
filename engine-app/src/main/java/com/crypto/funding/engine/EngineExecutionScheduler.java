package com.crypto.funding.engine;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EngineExecutionScheduler
{
    private final EngineProperties properties;
    private final EngineExecutionService executionService;

    public EngineExecutionScheduler( EngineProperties properties, EngineExecutionService executionService )
    {
        this.properties = properties;
        this.executionService = executionService;
    }

    @Scheduled(fixedDelayString = "${engine.execution-loop-interval-ms:1000}")
    public void runLoop()
    {
        if( properties.isExecutionLoopEnabled() )
        {
            executionService.runOnce( false );
        }
    }
}
