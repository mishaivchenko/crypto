# Metrics Publishing Requirements

Primary classes: `EngineMetricsPublisher`, `EngineMetricsPublishProperties`.

- `ENG-PUB-001`: the metrics publisher assembles snapshots from plan summary, runtime state, and telemetry state without changing wire shape.
- `ENG-PUB-002`: metrics-publish properties stay disabled by default and clamp the publish interval to the minimum supported value.
- `ENG-PUB-003`: `EngineMetricsPublisher.publishOnSchedule()` delegates to `publishSnapshot()` without changing snapshot assembly semantics.
- `ENG-PUB-004`: `EngineMetricsPublisher.publishOnSchedule()` swallows exceptions so a transient monitor 500 does not stop the publish schedule.
