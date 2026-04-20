package com.crypto.funding.application.port;

import com.crypto.funding.domain.execution.OrderAttempt;
import com.crypto.funding.domain.execution.OrderIntent;

public interface ExecutionPort
{
    OrderAttempt submitOrder( Long armedTradeId, String venue, String symbol, OrderIntent intent );
}
