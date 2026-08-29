package com.crypto.funding.application.observability;

import com.crypto.funding.contract.engine.EngineMetricsSnapshot;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "monitor.engine-metrics", name = "enabled", havingValue = "true")
public class EngineMetricsSnapshotStore {
    private final AtomicReference<EngineMetricsSnapshot> snapshotRef = new AtomicReference<>();

    public void update(EngineMetricsSnapshot snapshot) {
        snapshotRef.set(snapshot);
    }

    public EngineMetricsSnapshot current() {
        return snapshotRef.get();
    }
}
