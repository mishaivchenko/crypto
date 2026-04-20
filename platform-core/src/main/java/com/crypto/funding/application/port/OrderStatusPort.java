package com.crypto.funding.application.port;

import com.crypto.funding.domain.execution.OrderAttemptStatus;

import java.util.Optional;

public interface OrderStatusPort
{
    Optional<OrderAttemptStatus> fetchOrderStatus( String venue, String externalOrderId );
}
