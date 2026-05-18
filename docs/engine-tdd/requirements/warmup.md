# Warmup Probe Requirements

## ENG-WRM-018 — Concurrent warmup idempotency

**Requirement:** When two threads call `EngineExecutionService.runOnce(false)` concurrently for the same plan that is in the warmup window, warmup probes must fire exactly once. The second thread must not execute `executeWarmupProbes` at all.

**Mechanism:** `warmupByTrade.putIfAbsent(armedTradeId, WarmupCalibration.pending())` atomically claims the slot before probes execute. Only the thread that receives `null` (won the race) proceeds to probe.

**Why this matters:** Warmup probes consume HTTP budget (up to 250 ms). Duplicate probes waste network resources, report redundant latency samples, and post redundant `WarmupCalibrationRequest` to monitor — potentially overwriting a good sample with a later one. Under the scheduler tick (250 ms) + `runTarget` REST API concurrency, duplicate probe execution was possible before this fix.

**Test:** `EngineExecutionServiceWarmupTest.concurrentRunOnceFiresWarmupProbesExactlyOnce`
