package com.crypto.funding.application.port;

import com.crypto.funding.contract.engine.EngineExecutionPlan;
import com.crypto.funding.domain.execution.OrderAttempt;
import com.crypto.funding.domain.execution.OrderIntent;

public interface ExecutionPort {
    OrderAttempt submitOrder(EngineExecutionPlan plan, OrderIntent intent, boolean reduceOnly);
}
