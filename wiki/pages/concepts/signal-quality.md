---
title: "Signal Quality"
type: concept
tags: [signal, filtering, quality, candidate]
sources: []
updated: 2026-05-09
---

# Signal Quality

_Stub — expand on first relevant ingest._

Not every high funding rate is worth scalping. Quality filters avoid paying spread + fees for insufficient yield.

## Minimum Threshold

Rule of thumb: funding yield must exceed 2× (taker fee + estimated spread). At 0.06% taker fee per side = 0.12% round-trip cost. Funding rate must be > ~0.15–0.20% to be worth attempting.

## Stability

Predicted rate vs. realized rate can diverge. A rate that drops to near-zero before charge time is a false signal.

## Venue Basis

Same instrument can show different rates on different venues. Arbitrage opportunity if spread is tight enough on both.

## Codebase Mapping

- `SignalCandidate`: created from uainvest API, status `DETECTED → REVIEWED → ACCEPTED/REJECTED`
- Operator is the final quality gate — no automated acceptance currently
- `FundingEvent` created only after explicit operator `APPROVED` review decision

## Related

- [[funding-mechanics]]
- [[uainvest-api]]
- [[execution-strategy]]
