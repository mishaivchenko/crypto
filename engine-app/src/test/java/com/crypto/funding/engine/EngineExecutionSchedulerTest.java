package com.crypto.funding.engine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EngineExecutionSchedulerTest
{
    // REQ: ENG-SCH-001
    @Test
    void doesNothingWhenScheduledLoopIsDisabled()
    {
        EngineRuntimeControlService runtimeControlService = mock( EngineRuntimeControlService.class );
        EngineExecutionService executionService = mock( EngineExecutionService.class );
        when( runtimeControlService.shouldRunScheduledLoop() ).thenReturn( false );

        new EngineExecutionScheduler( runtimeControlService, executionService ).runLoop();

        verify( executionService, never() ).runOnce( false );
    }

    // REQ: ENG-SCH-002
    @Test
    void delegatesRunOnceWhenScheduledLoopIsEnabled()
    {
        EngineRuntimeControlService runtimeControlService = mock( EngineRuntimeControlService.class );
        EngineExecutionService executionService = mock( EngineExecutionService.class );
        when( runtimeControlService.shouldRunScheduledLoop() ).thenReturn( true );

        new EngineExecutionScheduler( runtimeControlService, executionService ).runLoop();

        verify( executionService ).runOnce( false );
    }

    // REQ: ENG-SCH-003
    @Test
    void swallowsRunOnceExceptionSoSchedulerKeepsRunning()
    {
        EngineRuntimeControlService runtimeControlService = mock( EngineRuntimeControlService.class );
        EngineExecutionService executionService = mock( EngineExecutionService.class );
        when( runtimeControlService.shouldRunScheduledLoop() ).thenReturn( true );
        when( executionService.runOnce( false ) ).thenThrow( new RuntimeException( "monitor 500" ) );

        assertThatNoException().isThrownBy( () -> new EngineExecutionScheduler( runtimeControlService, executionService ).runLoop() );
    }
}
