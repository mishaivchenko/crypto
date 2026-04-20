package com.crypto.funding.engine;

import com.crypto.funding.contract.engine.EngineExecutionRunResponse;
import com.crypto.funding.contract.engine.EngineExecutionPlan;
import com.crypto.funding.contract.engine.EngineSummaryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/engine")
public class EngineController
{
    private final EnginePlanService enginePlanService;
    private final EngineExecutionService engineExecutionService;

    public EngineController( EnginePlanService enginePlanService, EngineExecutionService engineExecutionService )
    {
        this.enginePlanService = enginePlanService;
        this.engineExecutionService = engineExecutionService;
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

    @PostMapping("/execution/run-once")
    public EngineExecutionRunResponse runOnce( @RequestParam(defaultValue = "false") boolean force )
    {
        return engineExecutionService.runOnce( force );
    }
}
