package com.crypto.funding.application.observability;

import com.crypto.funding.contract.engine.EngineMetricsSnapshot;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "monitor.engine-metrics", name = "enabled", havingValue = "true")
public class EngineMetricsIngestService
{
    private final EngineMetricsSnapshotNormalizer normalizer;
    private final EngineMetricsSnapshotStore snapshotStore;

    public EngineMetricsIngestService(
        EngineMetricsSnapshotNormalizer normalizer,
        EngineMetricsSnapshotStore snapshotStore
    )
    {
        this.normalizer = normalizer;
        this.snapshotStore = snapshotStore;
    }

    public void ingest( EngineMetricsSnapshot snapshot )
    {
        snapshotStore.update( normalizer.normalize( snapshot ) );
    }
}
