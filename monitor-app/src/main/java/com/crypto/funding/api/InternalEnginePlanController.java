package com.crypto.funding.api;

import com.crypto.funding.application.engine.MonitorEnginePlanService;
import com.crypto.funding.contract.engine.EngineExecutionPlan;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/v1/engine")
public class InternalEnginePlanController
{
    private final MonitorEnginePlanService enginePlanService;

    public InternalEnginePlanController( MonitorEnginePlanService enginePlanService )
    {
        this.enginePlanService = enginePlanService;
    }

    @GetMapping("/plans")
    public List<EngineExecutionPlan> plans()
    {
        return enginePlanService.listPlans();
    }

    @GetMapping("/plans/{armedTradeId}")
    public EngineExecutionPlan plan( @PathVariable Long armedTradeId )
    {
        return enginePlanService.getPlan( armedTradeId );
    }
}
