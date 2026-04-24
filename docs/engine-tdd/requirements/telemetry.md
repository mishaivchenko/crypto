# Telemetry Requirements

Primary class: `EngineTelemetryService`.

- `ENG-TEL-001`: averages stay zero-safe before any activity and after sparse activity.
- `ENG-TEL-002`: failure classification stays stable for `CANCELLED`, `REJECTED`, `FAILED`, and `EXPIRED`.
- `ENG-TEL-003`: per-venue submit counts and duration aggregates normalize venue names once and stay deterministic.
- `ENG-TEL-004`: forced-run telemetry is retained separately from scheduled-run telemetry.
