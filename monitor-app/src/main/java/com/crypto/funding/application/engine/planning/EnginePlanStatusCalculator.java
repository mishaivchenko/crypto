package com.crypto.funding.application.engine.planning;

import com.crypto.funding.config.MonitorEnginePlanProperties;
import com.crypto.funding.contract.engine.EnginePlanStatus;
import com.crypto.funding.domain.trade.ArmedTrade;
import com.crypto.funding.domain.trade.ArmedTradeState;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class EnginePlanStatusCalculator
{
    private final MonitorEnginePlanProperties engineProperties;
    private final EngineEntryAttemptScheduleBuilder attemptScheduleBuilder;

    public EnginePlanStatusCalculator(
        MonitorEnginePlanProperties engineProperties,
        EngineEntryAttemptScheduleBuilder attemptScheduleBuilder
    )
    {
        this.engineProperties = engineProperties;
        this.attemptScheduleBuilder = attemptScheduleBuilder;
    }

    public EnginePlanStatus deriveStatus( ArmedTrade trade, Instant now )
    {
        if( trade.state() == ArmedTradeState.CLOSED )
        {
            return EnginePlanStatus.CLOSED;
        }
        if( trade.state() == ArmedTradeState.CANCELLED || trade.state() == ArmedTradeState.FAILED )
        {
            return EnginePlanStatus.INVALID;
        }
        if( trade.plannedEntryAt() == null )
        {
            return EnginePlanStatus.INVALID;
        }

        Instant firstTrigger = attemptScheduleBuilder.firstTriggerAt( trade );
        Instant lastTarget = attemptScheduleBuilder.lastTargetAt( trade );
        Instant exit = trade.plannedExitAt();
        Instant overdueThreshold = lastTarget.plusSeconds( engineProperties.getOverdueGraceSeconds() );

        if( trade.state() == ArmedTradeState.ARMED || trade.state() == ArmedTradeState.ENTRY_PENDING || trade.state() == ArmedTradeState.ENTRY_ATTEMPTED )
        {
            if( now.isBefore( firstTrigger ) )
            {
                return EnginePlanStatus.WAITING_ENTRY;
            }
            if( exit != null && now.isAfter( exit ) )
            {
                return EnginePlanStatus.OVERDUE;
            }
            if( now.isAfter( overdueThreshold ) )
            {
                return EnginePlanStatus.OVERDUE;
            }
            return EnginePlanStatus.ENTRY_WINDOW;
        }

        if( trade.state() == ArmedTradeState.OPEN || trade.state() == ArmedTradeState.EXIT_PENDING )
        {
            if( exit == null )
            {
                return EnginePlanStatus.INVALID;
            }
            if( now.isBefore( exit ) )
            {
                return EnginePlanStatus.WAITING_EXIT;
            }
            return EnginePlanStatus.EXIT_WINDOW;
        }

        return EnginePlanStatus.INVALID;
    }
}
