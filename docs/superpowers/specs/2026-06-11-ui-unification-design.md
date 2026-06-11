# UI Unification — Enrichment Pipeline & Form Compaction

**Date:** 2026-06-11  
**Branch:** feat/enrichment-sprint1  
**Scope:** Visual & architectural unification of the monitor-app vanilla-JS UI

---

## Problem

The enrichment sprint (T-01..T-35) added Layer Blocks, a Venues Grid, and other enrichment components, but each arrived with its own visual language. The result is a fragmented UI:

- `layer-block` is flat with `border-left: 3px` and `border-radius: 4px` — inconsistent with the asymmetric rounded cards used everywhere else
- Enrichment components don't communicate the decorator pattern visually — layers look like unrelated items in a list, not a sequential pipeline
- Forms in Events (arm-trade) are oversized: `fieldset` wrappers with section labels consume 2× the vertical space needed for the actual data
- Freshness timestamps are static — no visual indication of data age ticking

---

## Design Decisions

### 1. `renderLayerPipeline` replaces `renderLayerBlock`

**Architecture:** Full replacement. `renderLayerBlock` is removed; all 12 call-sites are migrated to `renderLayerPipeline`.

**API:**
```js
renderLayerPipeline(layers, { screen })
// layers: Array<LayerDescriptor>
// LayerDescriptor: { layerType, layerName, decoratorName, timestamp, source, status, content, collapsed }
```

**Visual structure:**
- Single continuous vertical line spanning the full pipeline height — one absolute `div`, not segments per layer
- Line uses a CSS `linear-gradient` transitioning through each layer's color token (base → ai → liquidity → latency → execution)
- Each layer is a horizontal row: colored dot on the line + card (`border-radius: 10px 4px 14px 4px`) with gradient background matching `.list-item`
- Layer card header: `[LayerName] [StatusBadge] · [source] · [freshness ticker] [↻ refresh] [▾/▶]`
- Click on header toggles body open/closed; state persisted in `_store` keyed by `screen:layerType` (same as before)
- Header `min-height: 32px` on desktop, `min-height: 44px` on mobile (`@media (max-width: 760px)`) — preserves T-33 tap target requirement

**Freshness tickers:**
- Each pipeline instance registers a `setInterval` (1 second) on mount
- Ticker is a `<span data-seconds="N">` — JS increments `dataset.seconds` and updates text + color
- Color: green (`#4eba84`) < 30s → amber (`#d4a24a`) 30–120s → red (`#e05a5a`) > 120s
- Interval is cleaned up when the card is removed from DOM (via `MutationObserver` on the pipeline root)

**Per-layer refresh button (`↻ refresh`):**
- Not shown on `base` layer (source data, not refreshable from UI)
- Shown on `ai`, `liquidity`, `latency`, `execution` layers
- Wires to existing actions: `data-action="assess-card-liquidity"` for liquidity, `data-action="refresh-latency"` for latency — no new API endpoints needed
- On click: button shows `↻ …` spinner state, resets ticker to 0 on response

**CSS — new classes added to `styles.css`:**
```
.layer-pipeline          — container, position:relative, padding-left:26px
.layer-pipeline__line    — absolute, left:8px, top:6px, bottom:6px, width:2px, gradient
.layer-pipeline__row     — position:relative, margin-bottom:6px
.layer-pipeline__dot     — absolute, left:-22px, top:9px, 10×10px circle, per-type color + glow
.layer-pipeline__card    — border-radius:10px 4px 14px 4px, gradient bg, border per-type color at 0.2 opacity
.layer-pipeline__header  — flex row, padding:7px 10px, min-height:32px, cursor:pointer
.layer-pipeline__body    — collapsible content, border-top separator
.layer-refresh-btn       — small pill button, per-type color, font-size:9px
```

**Files changed:**
- `app/components/layer-block.js` → replaced entirely with `layer-pipeline.js`
- `app/shared.js` — migrate 3 calls
- `app/screens/events.js` — migrate 1 call
- `app/workflows/trade-detail.js` — migrate 6 calls
- `app/workflows/venue-detail.js` — migrate 3 calls
- `styles.css` — add `.layer-pipeline*` classes, remove `.layer-block*` classes
- `index.html` — update script import from `layer-block.js` → `layer-pipeline.js`

---

### 2. Compact form pattern

**Problem:** Forms use `min-height:42px` inputs, `<fieldset>` section wrappers with legends, and always-visible `<textarea>`. Visually: the same 10 fields occupy a full screen.

**Solution — applied to both `arm-event` (Events) and any future state-transition forms:**

**Field height:** `min-height: 32px`, `padding: 6px 9px` — replaces `min-height: 42px`, `padding: 10px 12px`

**Border radius on inputs:** `8px 3px 10px 3px` — asymmetric, matches the app language, smaller than full cards

**Section wrappers removed:** `<fieldset class="form-group">` with legend is replaced by visual grouping via `gap` in a CSS grid — no extra borders or labels

**Context chip:** A read-only summary chip at the top of the form shows pre-filled context (`funding rate`, `side`, key constraint) so the operator doesn't need to re-read the parent card. Not a form field — just `display:flex` info strip.

**Field grid:** `grid-template-columns: 1fr 1fr` for paired fields (entry/side, spacing/latency-adj, SL/TP). Three-column `1fr 1fr 80px` where the third field is a small integer (attempts, spacing).

**Optional fields:** SL, TP, and Preparation Note marked `(optional)` in their label. Note collapsed into `<details>` — closed by default, `▶ Add preparation note (optional)` expands it.

**New CSS class:** `.compact-form` — applies `32px` field heights and `8px 3px 10px 3px` radii. Existing `.drawer-form` is unchanged (backwards compat for other drawers); `event-detail.js` switches to `.compact-form`.

**Files changed:**
- `app/workflows/event-detail.js` — refactor `arm-event` form HTML
- `styles.css` — add `.compact-form`, `.compact-form input`, `.compact-form select`, `.compact-form .context-chip`

---

## What Does NOT Change

- Polling loop and overall data refresh strategy — unchanged
- Existing `getLayerCollapsed` / `clearLayerCollapsed` collapse-state API — re-exported from `layer-pipeline.js` for backwards compat during migration
- All i18n keys — `layer.base`, `layer.liquidity`, etc. remain in `i18n.js`
- Layer color CSS tokens (`--layer-base-border`, etc.) — remain in `:root`, reused by pipeline dots and cards
- Mobile tap targets (`min-height: 44px` on pipeline headers) — preserved

---

## Out of Scope

- Venues Grid styling — separate task
- Dashboard Pipeline Viz — separate task  
- Any backend changes
- New API endpoints for refresh (uses existing actions)

---

## Files Summary

| File | Change |
|------|--------|
| `app/components/layer-pipeline.js` | New — replaces layer-block.js |
| `app/components/layer-block.js` | Deleted |
| `app/shared.js` | Migrate 3 renderLayerBlock → renderLayerPipeline |
| `app/screens/events.js` | Migrate 1 call |
| `app/workflows/trade-detail.js` | Migrate 6 calls |
| `app/workflows/venue-detail.js` | Migrate 3 calls |
| `app/workflows/event-detail.js` | Compact form refactor |
| `styles.css` | Add `.layer-pipeline*`, `.compact-form`; remove `.layer-block*` |
| `index.html` | Update script import |
