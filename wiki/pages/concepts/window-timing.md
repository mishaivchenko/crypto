---
title: "Window Timing"
type: concept
tags: [timing, entry, latency, window]
sources: []
updated: 2026-05-09
---

# Window Timing

_Stub — expand on first relevant ingest._

The funding charge occurs at a precise moment. Scalping profit depends on entering before and exiting after.

## Entry Window

Entry target: `fundingChargeAt − effectiveLatency − safetyBuffer`

`effectiveLatency` = measured API round-trip + order execution lag (tracked by `VenueRequestTimingService` in the codebase).

## Exit Window

Exit immediately after charge confirmation or at planned exit time, whichever is earlier.

## Key Risk

Entering too early: exposed to price move before funding charge.
Entering too late: charge happens before fill, no funding collected.

## Codebase Mapping

- `ArmedTrade.plannedEntryAt` / `plannedExitAt`
- `EngineExecutionService`: checks `now > plannedEntryAt − effectiveLatency`
- `VenueRequestTimingService`: measures latency per venue
- Manual override: `latencyAdjustmentMs` in armed trade params

## Related

- [[funding-mechanics]]
- [[execution-strategy]]
- [[bybit]]
- [[gate]]
