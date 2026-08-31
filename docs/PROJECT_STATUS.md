# Project Status

_Updated: 2026-08-31 (Phase 0 cleanup reset)_

This file is a process-only stub. It intentionally does not describe product
capabilities; those narrative docs were removed in Phase 0 cleanup and should be
rewritten deliberately from the codebase and GitHub issue state.

## Source Of Truth

GitHub Issues and milestones are the authoritative task tracker. GitHub
Projects/boards are optional views only.

Legacy local trackers such as `tasks/`, `BACKLOG.md`, wiki memory dumps, and
Obsidian vault state are not authoritative repository artifacts.

## Active Work

- **Phase 0 - Foundation Restoration**: cleanup/reset is pending human approval
  in PR #186. Do not close issue #185 or the Phase 0 milestone without explicit
  human approval.
- **Phase 1 - Production Hardening**: next after Phase 0 approval.
- **Phase 2 - Go-Live**: planned after Phase 1 hardening.

## Retained Executable Docs

`docs/engine-tdd/` remains part of the verification system. It is retained as
the executable engine requirement mapping used by `engineTddDocsCheck` and
`engineTddGate`.

## Cleanup Boundary

Phase 0 cleanup is docs/status/repository hygiene only. It must not change
runtime behavior, implement Phase 1 work, add product features, or mix in remote
CLI/UI contract work.
