# Execution Requirements

Primary class: `EngineExecutionService`.

- `ENG-EXE-001`: `force=false` processes only `ENTRY_WINDOW` plans.
- `ENG-EXE-002`: `force=true` also processes `WAITING_ENTRY` and `OVERDUE` plans.
- `ENG-EXE-003`: plans with `null` or empty `entryAttempts` are skipped.
- `ENG-EXE-004`: future triggers are skipped when the run is not forced.
- `ENG-EXE-005`: attempt keys stay deterministic as `entry:<armedTradeId>:<attemptNumber>:<targetEntryAt>`.
- `ENG-EXE-006`: recorded order-attempt payloads mirror the execution result returned by the execution port.
- `ENG-EXE-007`: execution runs update telemetry for run counts, run timing, and submit status accounting.
- `ENG-EXE-008`: WAITING_EXIT plans with SL/TP set trigger early exit when mark price breaches threshold; plans without SL/TP or with unavailable mark price are not exited early.
- `ENG-WRM-001`: `warmupBudgetMs` returns `min(leadMs/2, 250)`.
- `ENG-WRM-002`: `perProbeTimeoutMs` returns `max(budget/count, 10)`.
- `ENG-WRM-003`: `percentile` uses nearest-rank formula; single-sample edge case returns that sample for any percentile.
- `ENG-WRM-004`: warm-up probes fire when `probeUrl` is set and `now` is inside the warmup window (before first calibrated trigger but within `warmupProbeLeadMs`).
- `ENG-WRM-005`: no probes when `probeUrl` is null or when outside the warmup window; backward compat with pre-feature behavior.
- `ENG-WRM-006`: warm-up is idempotent per `armedTradeId` within one service instance; second call for the same trade does not re-probe.
- `ENG-WRM-007`: when all probes timeout/throw, execution proceeds using `plan.effectiveEntryLatencyMs` as fallback (never delays order submission).
- `ENG-WRM-008`: `force=true` skips the warm-up phase entirely.
- `ENG-WRM-009`: terminal trade state (CLOSED/CANCELLED/FAILED) removes the cached `WarmupCalibration`, allowing warm-up to re-fire if the plan returns as active.
- `ENG-WRM-010`: `calibratedTrigger` uses warmup `p50+manual` when calibration exists; falls back to `plan.effectiveEntryLatencyMs` (or 0 if null) when no calibration.
- `ENG-WRM-011`: future calibrated trigger causes entry attempt to be skipped.
- `ENG-WRM-012`: warmup probe result is reported to monitor via `recordLatencySample` with operation=`warmup-probe`.
- `ENG-WRM-013`: null fields (`warmupProbeCount`, `warmupProbeLeadMs`, `effectiveEntryLatencyMs`) use correct defaults (3, 500, 0); budget deadline falls back to firstTrigger when budget exceeds time-to-trigger.
- `ENG-WRM-014`: `buildCalibration` with empty samples uses `plan.effectiveEntryLatencyMs` as fallback (or 0 when null); `fallbackUsed=true`.
- `ENG-WRM-015`: `buildCalibration` adds (not subtracts) manual latency to p50 for `calibratedEffectiveLatencyMs`; treats null `manualLatencyAdjustmentMs` as 0.
- `ENG-WRM-016`: probe timing sample uses subtraction (end - start) not addition; budget deadline is capped at `firstTrigger` when budget would exceed it.
- `ENG-WRM-017`: `calibratedEffectiveLatencyMs = p50 + manualLatency` (addition); observable via trigger time difference.
