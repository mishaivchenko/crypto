package com.crypto.funding.engine;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/engine")
public class EngineController
{
    private final EnginePlanService enginePlanService;

    public EngineController( EnginePlanService enginePlanService )
    {
        this.enginePlanService = enginePlanService;
    }

    @GetMapping("/summary")
    public EngineSummaryResponse summary()
    {
        return enginePlanService.summary();
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
