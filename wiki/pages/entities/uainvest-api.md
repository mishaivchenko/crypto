---
title: "uainvest Funding API"
type: entity
tags: [api, signal-source, funding-rate]
sources: []
updated: 2026-05-09
---

# uainvest Funding API

Primary signal source for `SignalCandidate` ingestion.

## Endpoint

```
GET https://uainvest.com.ua/api/funding?sort_by=funding&sort_dir=asc&limit=30
```

Default sort: ascending by funding rate (lowest first). For scalping targets, sort `desc` or filter client-side for high rates.

## Response Fields

_To be confirmed on first ingest of API documentation or live response sample._

Expected fields based on codebase usage:
- `symbol` — raw instrument symbol (needs normalization via `SymbolMapper`)
- `venue` / `exchange` — venue hint
- `funding_rate` — current or predicted rate
- `next_funding_time` — when charge occurs

## Polling Behavior

- Polled by `FundingApiCandidateSourceService` on a configurable interval
- Creates/updates `SignalCandidate` records (status: `DETECTED`)
- Does **not** create `FundingEvent` — operator review required

## Symbol Normalization

Raw symbols from this API go through `SymbolMapper` + `InstrumentRegistryService` before storage. Mapping may fail for new instruments not in `instrument_metadata` table.

## Related

- [[signal-quality]]
- [[bybit]]
- [[gate]]
- [[okx]]
