package com.crypto.funding.api;

import com.crypto.funding.application.engine.MonitorEnginePlanService;
import com.crypto.funding.application.execution.EngineLifecycleRecordService;
import com.crypto.funding.application.execution.OrderAttemptRecordService;
import com.crypto.funding.contract.engine.EngineExecutionPlan;
import com.crypto.funding.contract.engine.EngineOrderAttemptRecordRequest;
import com.crypto.funding.contract.engine.EngineOrderAttemptResponse;
import com.crypto.funding.contract.engine.EnginePositionRecordRequest;
import com.crypto.funding.contract.engine.EnginePositionResponse;
import com.crypto.funding.contract.engine.EngineTradeOutcomeRecordRequest;
import com.crypto.funding.contract.engine.EngineTradeOutcomeResponse;
import com.crypto.funding.contract.engine.EngineTradeStateUpdateRequest;
import com.crypto.funding.contract.engine.EngineTradeStateUpdateResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/v1/engine")
public class InternalEnginePlanController
{
    private final MonitorEnginePlanService enginePlanService;
    private final OrderAttemptRecordService orderAttemptRecordService;
    private final EngineLifecycleRecordService lifecycleRecordService;

    public InternalEnginePlanController(
        MonitorEnginePlanService enginePlanService,
        OrderAttemptRecordService orderAttemptRecordService,
        EngineLifecycleRecordService lifecycleRecordService
    )
    {
        this.enginePlanService = enginePlanService;
        this.orderAttemptRecordService = orderAttemptRecordService;
        this.lifecycleRecordService = lifecycleRecordService;
    }

    @GetMapping("/plans")
    public List<EngineExecutionPlan> plans( @RequestParam(defaultValue = "false") boolean includeAll )
    {
        return enginePlanService.listPlans( includeAll );
    }

    @GetMapping("/plans/{armedTradeId}")
    public EngineExecutionPlan plan( @PathVariable Long armedTradeId )
    {
        return enginePlanService.getPlan( armedTradeId );
    }

    @PostMapping("/order-attempts")
    public EngineOrderAttemptResponse recordOrderAttempt( @RequestBody EngineOrderAttemptRecordRequest request )
    {
        return orderAttemptRecordService.record( request );
    }

    @PostMapping("/trades/{armedTradeId}/state")
    public EngineTradeStateUpdateResponse updateTradeState(
        @PathVariable Long armedTradeId,
        @RequestBody EngineTradeStateUpdateRequest request
    )
    {
        return lifecycleRecordService.updateTradeState( armedTradeId, request );
    }

    @PostMapping("/positions")
    public EnginePositionResponse recordPosition( @RequestBody EnginePositionRecordRequest request )
    {
        return lifecycleRecordService.recordPosition( request );
    }

    @PostMapping("/outcomes")
    public EngineTradeOutcomeResponse recordOutcome( @RequestBody EngineTradeOutcomeRecordRequest request )
    {
        return lifecycleRecordService.recordTradeOutcome( request );
    }
}
