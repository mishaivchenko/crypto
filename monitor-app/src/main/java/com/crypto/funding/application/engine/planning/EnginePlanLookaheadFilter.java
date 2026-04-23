package com.crypto.funding.application.engine.planning;

import com.crypto.funding.config.MonitorEnginePlanProperties;
import com.crypto.funding.contract.engine.EngineExecutionPlan;
import com.crypto.funding.contract.engine.EnginePlanStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class EnginePlanLookaheadFilter
{
    private final MonitorEnginePlanProperties engineProperties;

    public EnginePlanLookaheadFilter( MonitorEnginePlanProperties engineProperties )
    {
        this.engineProperties = engineProperties;
    }

    public boolean shouldInclude( EngineExecutionPlan plan, boolean includeAll )
    {
        if( includeAll )
        {
            return true;
        }
        if( plan.status() == EnginePlanStatus.ENTRY_WINDOW || plan.status() == EnginePlanStatus.EXIT_WINDOW || plan.status() == EnginePlanStatus.OVERDUE )
        {
            return true;
        }
        if( plan.millisUntilAction() == null )
        {
            return false;
        }
        long lookaheadMillis = Duration.ofMinutes( engineProperties.getLookaheadMinutes() ).toMillis();
        return plan.millisUntilAction() <= lookaheadMillis;
    }
}
