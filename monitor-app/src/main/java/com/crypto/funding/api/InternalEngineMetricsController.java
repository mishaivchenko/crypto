package com.crypto.funding.api;

import com.crypto.funding.application.observability.EngineMetricsSnapshotStore;
import com.crypto.funding.contract.engine.EngineMetricsSnapshot;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/engine")
@ConditionalOnProperty(prefix = "monitor.engine-metrics", name = "enabled", havingValue = "true")
public class InternalEngineMetricsController
{
    private final EngineMetricsSnapshotStore snapshotStore;

    public InternalEngineMetricsController( EngineMetricsSnapshotStore snapshotStore )
    {
        this.snapshotStore = snapshotStore;
    }

    @PostMapping("/metrics-snapshot")
    public ResponseEntity<Void> ingestSnapshot( @RequestBody EngineMetricsSnapshot snapshot )
    {
        snapshotStore.update( snapshot );
        return ResponseEntity.accepted().build();
    }
}
