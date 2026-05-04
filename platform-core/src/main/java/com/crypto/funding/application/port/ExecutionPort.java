package com.crypto.funding.application.port;

import com.crypto.funding.domain.execution.OrderAttempt;
import com.crypto.funding.domain.execution.OrderIntent;
import com.crypto.funding.contract.engine.EngineExecutionPlan;

public interface ExecutionPort
{
    OrderAttempt submitOrder( EngineExecutionPlan plan, OrderIntent intent, boolean reduceOnly );
}
