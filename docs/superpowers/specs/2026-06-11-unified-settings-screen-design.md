# Unified Settings Screen

**Date:** 2026-06-11
**Issues:** #153 (epic), #154, #155, #156, #157
**Branch:** feat/settings-screen (to be cut from main)
**Scope:** Consolidate all scattered UI controls into a single Settings screen

---

## Problem

UI controls are spread across three unrelated locations:

- **Dashboard Dev Tools** — Engine loop, live orders, kill switch, loop interval, Run Once, LAB
- **Auto-Approval screen** (own sidebar entry) — global enable/disable, rules list, add/edit/delete rule
- **Topbar / inline** — AI Advisor toggle, language switch, access mode

This creates friction during tuning sessions: an operator adjusting engine parameters must context-switch between screens. As the system grows, adding more controls will worsen fragmentation.

---

## Design Decisions

### 1. New sidebar screen `#settings`

A dedicated Settings screen is added as a sidebar nav item. Route: `#settings`.

**Not** a drawer (conflicts with existing trade/event detail drawers) and **not** a dashboard extension (dashboard already crowded).

### 2. Two-column layout

```
┌─────────────────────────┬──────────────────────────┐
│  ⚡ Engine Control       │  🤖 AI Advisor            │
│                         │  ┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄  │
│  [toggles]              │  🔄 Pipeline Automation   │
│  [Run Once] [LAB]       │  ┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄  │
│                         │  🌐 Global                │
└─────────────────────────┴──────────────────────────┘
```

Left column: Engine Control (largest section, benefits from full height).
Right column: three stacked cards — AI Advisor, Pipeline Automation, Global.

### 3. Auto-Approval fully moves to Settings

`auto-approval.js` is deleted. Its sidebar entry is removed. All auto-approval functionality
(global toggle, rules list, add/edit/delete rule) lives in Settings → Pipeline Automation card.

The sidebar entry is removed to reduce nav clutter; operators find it via Settings.

### 4. Run Once and LAB — modals unchanged

The buttons move to Settings → Engine Control section. The modal mechanic (overlay with venue/symbol/notional fields) is **not changed**. Only the trigger location moves.

---

## Section Specifications

### ⚡ Engine Control (left column)

Four pill-toggle controls in a 2×2 grid:

| Control | API endpoint | States |
|---------|-------------|--------|
| Execution Loop | `POST /internal/engine/loop` | ON / OFF |
| Live Orders | `POST /internal/engine/live-orders` | ON / OFF |
| Kill Switch | `POST /internal/engine/kill` | ACTIVE / OFF |
| Loop interval | `PATCH /internal/engine/config` | number (ms) |

Below the grid: `▷ Run Once` and `🧪 LAB Test Run` buttons — same modal handlers as today.

All state fetched from existing `GET /internal/engine/status` on screen mount.

### 🤖 AI Advisor (right column, top card)

- ON/OFF pill toggle — same `aiAdvisorEnabled` flag, same API call
- Read-only stat chips: GO count / WATCH count / PASS count (from existing AI advisor state)
- DeepSeek badge label

### 🔄 Pipeline Automation (right column, middle card)

Full content of current `auto-approval.js`:

- Global enable/disable pill toggle
- Rules list: each rule shows name, priority, enabled state + edit/delete buttons
- `+ Add rule` button opens existing add-rule modal

No backend changes. All existing API calls unchanged.

### 🌐 Global (right column, bottom card)

- Language: EN / RU pill toggle (same `setLang()` call)
- Access Mode: TESTNET / PROD pill toggle

Both already exist in the topbar; they are **moved** here (removed from topbar).

---

## Navigation Changes

**Sidebar before:**
```
Dashboard · Signals · Events · Trades · History · Auto-Approval · Venues
```

**Sidebar after:**
```
Dashboard · Signals · Events · Trades · History · Venues · ⚙ Settings
```

Auto-Approval removed. Settings added at the end.

---

## Files

| File | Change |
|------|--------|
| `app/screens/settings.js` | **Create** — new screen with two-column layout |
| `app.js` | Add `settings` route + nav item; remove `auto-approval` route + nav item |
| `app/screens/dashboard.js` | Remove Dev Tools section (engine toggles, Run Once, LAB buttons) |
| `app/screens/auto-approval.js` | **Delete** — content moved to settings.js |
| `styles.css` | Add `.settings-grid` (two-column layout) if not covered by existing grid utilities |

---

## What Does NOT Change

- All backend API endpoints — no server-side changes
- Modal mechanic for Run Once and LAB
- Auto-approval rules data model and backend service
- AI Advisor DeepSeek integration
- Language/access-mode toggle logic — same functions, different trigger location
- Existing screen routes (dashboard, signals, events, trades, history, venues)

---

## Out of Scope

- Settings persistence across page reload (no localStorage for engine toggles — already stateless via API)
- User account / profile settings
- Notification preferences
- Any engine-app changes
