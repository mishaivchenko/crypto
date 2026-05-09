package com.crypto.funding.engine;

import com.crypto.funding.contract.engine.EngineExecutionRunResponse;
import com.crypto.funding.contract.engine.EngineExecutionPlan;
import com.crypto.funding.contract.engine.EngineExecutionTargetRequest;
import com.crypto.funding.contract.engine.EngineRuntimeControlRequest;
import com.crypto.funding.contract.engine.EngineRuntimeControlResponse;
import com.crypto.funding.contract.engine.EngineSummaryResponse;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final EngineRuntimeControlService engineRuntimeControlService;

    public EngineController(
        EnginePlanService enginePlanService,
        EngineExecutionService engineExecutionService,
        EngineRuntimeControlService engineRuntimeControlService
    )
    {
        this.enginePlanService = enginePlanService;
        this.engineExecutionService = engineExecutionService;
        this.engineRuntimeControlService = engineRuntimeControlService;
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

    @PostMapping("/execution/target")
    public EngineExecutionRunResponse runTarget( @RequestBody EngineExecutionTargetRequest request )
    {
        return engineExecutionService.runTarget(
            request.armedTradeId(),
            request.phase(),
            Boolean.TRUE.equals( request.force() )
        );
    }

    @GetMapping("/runtime")
    public EngineRuntimeControlResponse runtime()
    {
        return engineRuntimeControlService.snapshot();
    }

    @PostMapping("/runtime")
    public EngineRuntimeControlResponse updateRuntime( @RequestBody EngineRuntimeControlRequest request )
    {
        return engineRuntimeControlService.update( request );
    }
}
