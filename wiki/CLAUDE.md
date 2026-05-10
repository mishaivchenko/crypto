# Wiki Schema — funding-window-scalping

This file governs how Claude Code maintains the wiki for the **funding-window-scalping** knowledge base.

## Purpose

A persistent, compounding wiki about crypto funding rate scalping: mechanics, venues, timing windows, execution strategies, and edge cases. Raw sources go in `raw/`; LLM-generated pages go in `pages/`. You source and direct; I write and maintain.

---

## Directory Layout

```
wiki/
  CLAUDE.md          ← this file (schema + rules)
  index.md           ← content catalog, updated on every ingest
  log.md             ← append-only chronological record
  raw/               ← immutable source documents (you drop files here)
  pages/
    concepts/        ← how things work (funding mechanics, window timing, etc.)
    entities/        ← specific things (venues, instruments, APIs)
    sources/         ← one summary page per raw source
```

`raw/` is read-only for me. I never modify files there.

---

## Page Format

Every wiki page starts with YAML frontmatter:

```yaml
---
title: "<page title>"
type: concept | entity | source | analysis
tags: [tag1, tag2]
sources: [filename-in-raw]
updated: YYYY-MM-DD
---
```

Body: plain markdown. Use `[[WikiLink]]` for internal references (Obsidian-compatible). Keep pages factual and dense — no filler prose.

---

## Operations

### Ingest

When you say "ingest: <source>" or drop a file in `raw/`:

1. Read the source.
2. Discuss key takeaways with you (brief, 3–5 bullets).
3. Write/update `pages/sources/<slug>.md` — summary of this source.
4. Update or create relevant `pages/concepts/` and `pages/entities/` pages.
5. Note contradictions with existing pages explicitly.
6. Update `index.md`.
7. Append to `log.md`: `## [YYYY-MM-DD] ingest | <title>`.

A single source may touch 5–15 pages. That's expected and correct.

### Query

When you ask a question:

1. Read `index.md` to identify relevant pages.
2. Read those pages.
3. Answer with citations (`[[PageName]]` or `raw/filename`).
4. If the answer is substantive — a comparison, an analysis, a discovered connection — offer to file it as a new page.

### Lint

When you say "lint":

1. Scan all pages for: contradictions, stale claims, orphan pages (no inbound links), concepts mentioned but lacking a page, missing cross-references.
2. Report findings as a checklist.
3. Fix what you can; flag what needs a new source.

---

## Naming Conventions

- Source files in `raw/`: `YYYY-MM-DD-short-slug.md` (or original filename if clipped).
- Wiki pages: `kebab-case.md`.
- Log entries start with: `## [YYYY-MM-DD] <operation> | <title>` — parseable with `grep "^## \[" log.md`.

---

## Domain Context

This wiki is specifically about **funding rate scalping**:
- Funding windows: the 8h/4h/1h cycles across venues (Bybit, Gate, OKX, Bitget, KuCoin)
- Timing: when funding is charged, how to enter/exit around the window
- Signal detection: what makes a funding rate worth scalping
- Execution: entry bursts, latency compensation, notional sizing
- Risk: liquidation proximity, spread cost vs. funding yield, venue differences

Key entities already in the codebase: `SignalCandidate`, `FundingEvent`, `ArmedTrade`, `OrderAttempt`.

---

## Session Continuity

At the start of a session, read `log.md` (last 10 entries) and `index.md` to restore context. Do not ask me to re-explain what we covered before — derive it from the wiki.
