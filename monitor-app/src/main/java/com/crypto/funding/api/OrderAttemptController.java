package com.crypto.funding.api;

import com.crypto.funding.application.execution.OrderAttemptRecordService;
import com.crypto.funding.contract.engine.EngineOrderAttemptResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class OrderAttemptController
{
    private final OrderAttemptRecordService orderAttemptRecordService;

    public OrderAttemptController( OrderAttemptRecordService orderAttemptRecordService )
    {
        this.orderAttemptRecordService = orderAttemptRecordService;
    }

    @GetMapping("/order-attempts")
    public List<EngineOrderAttemptResponse> list()
    {
        return orderAttemptRecordService.listAll();
    }

    @GetMapping("/armed-trades/{armedTradeId}/order-attempts")
    public List<EngineOrderAttemptResponse> listByArmedTrade( @PathVariable Long armedTradeId )
    {
        return orderAttemptRecordService.listByArmedTrade( armedTradeId );
    }
}
