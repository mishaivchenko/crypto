package com.crypto.funding.api;

import com.crypto.funding.api.dto.EngineRunOnceResponse;
import com.crypto.funding.api.dto.EngineRuntimeSettingsRequest;
import com.crypto.funding.api.dto.EngineRuntimeSettingsResponse;
import com.crypto.funding.application.monitor.EngineControlService;
import com.crypto.funding.contract.engine.EngineExecutionRunResponse;
import com.crypto.funding.contract.engine.EngineRuntimeControlRequest;
import com.crypto.funding.contract.engine.EngineRuntimeControlResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/monitor/dev")
public class MonitorDevToolsController
{
    private final EngineControlService engineControlService;

    public MonitorDevToolsController( EngineControlService engineControlService )
    {
        this.engineControlService = engineControlService;
    }

    @PostMapping("/engine/run-once")
    public EngineRunOnceResponse runEngineOnce( @RequestParam(defaultValue = "true") boolean force )
    {
        EngineExecutionRunResponse response = engineControlService.runOnce( force );
        return new EngineRunOnceResponse(
            response.startedAt(),
            response.finishedAt(),
            response.force(),
            response.plansScanned(),
            response.attemptsSubmitted(),
            response.attemptsSkipped(),
            response.results()
        );
    }

    @GetMapping("/engine/runtime")
    public EngineRuntimeSettingsResponse runtime()
    {
        return toResponse( engineControlService.runtime() );
    }

    @PostMapping("/engine/runtime")
    public EngineRuntimeSettingsResponse updateRuntime( @Valid @RequestBody EngineRuntimeSettingsRequest request )
    {
        return toResponse( engineControlService.updateRuntime( new EngineRuntimeControlRequest(
            request.executionLoopEnabled(),
            request.executionLoopIntervalMs()
        ) ) );
    }

    private EngineRuntimeSettingsResponse toResponse( EngineRuntimeControlResponse response )
    {
        return new EngineRuntimeSettingsResponse(
            response.module(),
            response.version(),
            response.executionLoopEnabled(),
            response.executionLoopIntervalMs(),
            response.minimumExecutionLoopIntervalMs(),
            response.runtimeUpdatedAt(),
            response.lastRunStartedAt(),
            response.lastRunFinishedAt(),
            response.lastRunForced(),
            response.lastPlansScanned(),
            response.lastAttemptsSubmitted(),
            response.lastAttemptsSkipped(),
            response.lastExecutionRunDurationMs(),
            response.lastForcedRunStartedAt(),
            response.lastForcedRunFinishedAt(),
            response.lastForcedPlansScanned(),
            response.lastForcedAttemptsSubmitted(),
            response.lastForcedAttemptsSkipped(),
            response.lastForcedRunDurationMs()
        );
    }
}
