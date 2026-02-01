package com.crypto.funding.persistence.service;

import com.crypto.funding.persistence.model.OrderExecutionTimeEntity;
import com.crypto.funding.persistence.repository.OrderExecutionTimeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class OrderExecutionTimeStore
{
    private final OrderExecutionTimeRepository repo;

    public OrderExecutionTimeStore(OrderExecutionTimeRepository repo)
    {
        this.repo = repo;
    }

    @Transactional
    public OrderExecutionTimeEntity save(String exchange, String orderId, String symbol, long serverReceivedAt,
                                         Long exchangeExecutedAt, String timestampSource, Instant fundingAt )
    {
        OrderExecutionTimeEntity entity = new OrderExecutionTimeEntity(
            exchange,
            orderId,
            symbol,
            serverReceivedAt,
            exchangeExecutedAt,
            timestampSource,
            fundingAt
        );
        return repo.save(entity);
    }
}
