package com.crypto.funding.api;

import com.crypto.funding.api.dto.DevTestRunCreateRequest;
import com.crypto.funding.api.dto.DevTestRunExecutionResponse;
import com.crypto.funding.api.dto.DevTestRunOptionsResponse;
import com.crypto.funding.api.dto.DevTestRunPhaseRequest;
import com.crypto.funding.api.dto.DevTestRunResponse;
import com.crypto.funding.api.dto.EngineRunOnceResponse;
import com.crypto.funding.api.dto.EngineRuntimeSettingsRequest;
import com.crypto.funding.api.dto.EngineRuntimeSettingsResponse;
import com.crypto.funding.application.monitor.DevTestRunService;
import com.crypto.funding.application.monitor.EngineControlService;
import com.crypto.funding.application.observability.EngineMetricsSnapshotStore;
import com.crypto.funding.contract.engine.EngineExecutionRunResponse;
import com.crypto.funding.contract.engine.EngineExecutionTargetPhase;
import com.crypto.funding.contract.engine.EngineMetricsSnapshot;
import com.crypto.funding.contract.engine.EngineRuntimeControlRequest;
import com.crypto.funding.contract.engine.EngineRuntimeControlResponse;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/v2/monitor/dev")
public class MonitorDevToolsController
{
    private final EngineControlService engineControlService;
    private final DevTestRunService devTestRunService;
    private final Optional<EngineMetricsSnapshotStore> snapshotStore;

    public MonitorDevToolsController(
        EngineControlService engineControlService,
        DevTestRunService devTestRunService,
        Optional<EngineMetricsSnapshotStore> snapshotStore
    )
    {
        this.engineControlService = engineControlService;
        this.devTestRunService = devTestRunService;
        this.snapshotStore = snapshotStore;
    }

    @GetMapping("/engine/metrics")
    public ResponseEntity<EngineMetricsSnapshot> metrics()
    {
        return snapshotStore
            .map( store -> store.current() )
            .filter( snapshot -> snapshot != null )
            .map( ResponseEntity::ok )
            .orElse( ResponseEntity.noContent().build() );
    }

    @PostMapping("/engine/run-once")
    public EngineRunOnceResponse runEngineOnce( @RequestParam(defaultValue = "true") boolean force )
    {
        return EngineRunOnceResponse.from( engineControlService.runOnce( force ) );
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
            request.executionLoopIntervalMs(),
            request.liveOrderEnabled(),
            request.killSwitchEnabled()
        ) ) );
    }

    @GetMapping("/test-runs/options")
    public DevTestRunOptionsResponse devTestRunOptions()
    {
        return devTestRunService.options();
    }

    @PostMapping("/test-runs")
    @ResponseStatus(HttpStatus.CREATED)
    public DevTestRunResponse createDevTestRun( @Valid @RequestBody DevTestRunCreateRequest request )
    {
        return devTestRunService.create( request.venue(), request.symbol(), request.notionalUsd() );
    }

    @PostMapping("/test-runs/{armedTradeId}/entry")
    public DevTestRunExecutionResponse runDevTestEntry(
        @PathVariable Long armedTradeId,
        @RequestBody(required = false) DevTestRunPhaseRequest request
    )
    {
        return devTestRunService.runPhase(
            armedTradeId,
            EngineExecutionTargetPhase.ENTRY,
            request == null ? null : request.productionConfirm()
        );
    }

    @PostMapping("/test-runs/{armedTradeId}/exit")
    public DevTestRunExecutionResponse runDevTestExit(
        @PathVariable Long armedTradeId,
        @RequestBody(required = false) DevTestRunPhaseRequest request
    )
    {
        return devTestRunService.runPhase(
            armedTradeId,
            EngineExecutionTargetPhase.EXIT,
            request == null ? null : request.productionConfirm()
        );
    }

    private EngineRuntimeSettingsResponse toResponse( EngineRuntimeControlResponse response )
    {
        return new EngineRuntimeSettingsResponse(
            response.module(),
            response.version(),
            response.tradingVenueAccessMode(),
            response.liveOrderEnabled(),
            response.killSwitchEnabled(),
            response.liveEnabledVenues(),
            response.maxNotionalUsd(),
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
