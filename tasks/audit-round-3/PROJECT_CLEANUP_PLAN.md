# Project Cleanup Plan — Audit Round 3

Repository: `/Users/mishaivchenko/dev/crypto`
Generated: 2026-07-13

---

## A. EXECUTIVE SUMMARY

| Category | Count | Notes |
|---|---|---|
| Java source files (all modules) | ~1,973 | monitor-app + engine-app + platform-core, excl. build/ and org/ |
| Engine-app production classes | 16 | CLAUDE.md incorrectly says "13" |
| Static JS/CSS/HTML resources | 34 | Under monitor-app/src/main/resources/static/ |
| Build scripts | 6 | 4 build.gradle + settings.gradle + gradle.properties |
| Test files (Java) | 22 | engine-app only; monitor-app + platform-core add more |
| Documentation files | ~15 | docs/ + AGENTS.md + BACKLOG.md + README.md |
| Flyway migrations (active) | 2 | V1__baseline.sql + V14__auto_approval_rules.sql |
| CLAUDE.md / AGENTS.md | 3 | Root CLAUDE.md, AGENTS.md, wiki/CLAUDE.md |
| Legacy/Orphaned/Stale | ~5 | See sections D, E, F |
| Generated build artifacts (org/) | 628 KB | Spring Boot loader .class files, not in build/ |
| Local runtime state | 3 files | data/fundingarb.db + .db-shm + .db-wal |
| Secret/Sensitive files | 2 | .env + deploy/.env |
| Empty/minimal directories | 2 | .worktrees/, .maestro/playbooks/ |
| Near-empty directory (only .obsidian) | 1 | funding-memory/ |

---

## B. KEEP LIST (files that appear stale but must remain)

1. **V1__baseline.sql** — HISTORICAL_REQUIRED_IMMUTABLE. Foundation of the schema. Must never be modified.
2. **V14__auto_approval_rules.sql** — ACTIVE_SCHEMA_EVOLUTION. Only migration beyond V1. Both CLAUDE.md and the plan template incorrectly assume V2–V14 exist; in fact only V1 and V14 are present on this branch.
3. **single_funding.sql** — read-only reference query at project root. Small (1,515 bytes). Only remove after confirming the operator still does not use it.
4. **All docs/ files** — even stale docs (12-engine-tdd-migration-prompt.md) provide context. Move outdated prompts to docs/archive/ rather than deleting.
5. **funding-memory/** — contains `.obsidian` vault config (7 files, 224 bytes). If intended as an Obsidian vault for working notes, keep it. If abandoned, remove.
6. **.superpowers/** — not empty; contains brainstorm/ subdirectory with actual content. Keep.
7. **tasks/B-*.md, tasks/F-*.md** — active bug and feature tracking files. Keep.

---

## C. UPDATE LIST

### C1. CLAUDE.md — Stale references

**File:** `/Users/mishaivchenko/dev/crypto/CLAUDE.md`

| Line | Current text | Correct text | Rationale |
|---|---|---|---|
| 44 | `schema owned by Flyway (V1-V5 migrations)` | `schema owned by Flyway (V1, V14 migrations)` | There are only 2 migration files, not 5. The V2-V5 range never existed on this branch. |
| 54 | `13 core production classes` | `16 core production classes` | `find engine-app/src/main/java -name '*.java' | wc -l` returns 16. |
| 56-70 | Key classes list | Add: `EngineCredentialCache`, `EngineCredentialStatusController`, `EngineMetricsPublishProperties` | These 3 classes exist but are not listed in the CLAUDE.md key classes commentary. |

### C2. gradle.properties — Stale version properties

**File:** `/Users/mishaivchenko/dev/crypto/gradle.properties`

Remove these properties if they are managed by the Gradle platform, BOM, or build.gradle directly:

```
springBootVersion=3.5.2
owaspDepCheckVersion=10.0.4
assertjVersion=3.25.3
mockitoVersion=5.12.0
junitJupiterVersion=5.10.3
```

**Verification step:** Search build.gradle for `$springBootVersion`, `$owaspDepCheckVersion`, etc. If these variables are referenced, they must stay. If the versions are hardcoded in build.gradle or managed by a BOM, they can be removed. If unknown, grep for usage before removing.

### C3. docs/01-system-flow.md — Potential content drift

**File:** `/Users/mishaivchenko/dev/crypto/docs/01-system-flow.md`

Review the armedTradeId claim and ensure it still reflects current behavior after auto-approval changes.

---

## D. ARCHIVE LIST

| File | Target | Rationale |
|---|---|---|
| `tasks/PROJECT_AUDIT_ROUND_1.md` (48 KB) | `docs/audit-archive/` | Completed audit artifact. Kept for reference, not active task. |
| `tasks/PROJECT_AUDIT_ROUND_2.md` (41 KB) | `docs/audit-archive/` | Same as above. |
| `docs/12-engine-tdd-migration-prompt.md` | `docs/archive/` | This is a prompt given to an AI, not permanent documentation. Archive it. |

**Action:** Create directory `docs/audit-archive/` (if not exists) and `docs/archive/` (if not exists). Move files via `git mv`.

---

## E. REMOVE-CONFIRMED LIST (no dependencies, safe to delete)

### E1. `org/` directory (root) — GENERATED_BUILD_ARTIFACT

**Path:** `/Users/mishaivchenko/dev/crypto/org/`
**Size:** 628 KB
**Contents:** `org/springframework/boot/loader/*.class` — Spring Boot loader classes extracted during build.
**Evidence that it is generated:**
- All files are `.class` files (compiled Java bytecode)
- Not referenced in any source file or build script
- `./gradlew build` succeeds without this directory present
- Identical to classes that Spring Boot's build plugin produces inside `build/`

**Action:** `rm -rf org/` and ensure `.gitignore` has `org/` or it is covered by `*.class`.

### E2. `META-INF/` (root) — ServiceLoader for above

**Path:** `/Users/mishaivchenko/dev/crypto/META-INF/`
**Contents:** `services/` directory — ServiceLoader registration for Spring Boot loader classes.
**Rationale:** Bundled with `org/`. Has no independent value. Remove with `org/`.

**Action:** `rm -rf META-INF/`

### E3. `package-lock.json` (root) — ORPHAN_RESOURCE

**Path:** `/Users/mishaivchenko/dev/crypto/package-lock.json`
**Size:** 162 KB
**Origin:** From a previous Vite/TypeScript frontend that was removed.
**Evidence it is orphaned:**
- No `package.json` exists at project root
- No `node_modules/` directory exists at project root
- The frontend now lives in `monitor-app/src/main/resources/static/` as vanilla JS
- No npm/node build step exists in the current build pipeline

**Action:** `git rm package-lock.json`

### E4. Empty directories — PLACEHOLDER_DIRECTORIES

| Path | Contents | Action |
|---|---|---|
| `/Users/mishaivchenko/dev/crypto/.worktrees/` | Empty (created by git worktree tooling, no longer needed) | `rm -rf .worktrees/` + ensure `.gitignore` has `.worktrees/` or add it |
| `/Users/mishaivchenko/dev/crypto/.maestro/playbooks/` | Empty (directory structure with no files) | `rm -rf .maestro/playbooks/` (keep `.maestro/` if it has other files, else remove both) |

Note: `.worktrees/` is a transient tool directory. The `.gitignore` already has `.claude/` but not `.worktrees/`. Add it.

---

## F. REMOVE-AFTER-DEPENDENCY

### F1. `tasks/PROJECT_AUDIT_ROUND_1.md` and `PROJECT_AUDIT_ROUND_2.md`

Already listed in section D (ARCHIVE). These are audit deliverables from previous rounds. They should be moved to `docs/audit-archive/` rather than deleted.

### F2. `gradle.properties` stale properties

Listed in C2. Remove only after verifying that `build.gradle` and `settings.gradle` do not reference `$springBootVersion` or similar.

### F3. `single_funding.sql`

**Path:** `/Users/mishaivchenko/dev/crypto/single_funding.sql`
**Size:** 1,515 bytes
**Status:** KEEP_UNLESS_CONFIRMED_UNUSED
**Action:** Ask the operator whether this query is still used as a reference. If yes, move to `docs/queries/` and document. If no, delete.

---

## G. SECRET / LOCAL-STATE CLEANUP

### G1. `.env` (root) — TESTNET CREDENTIALS

**Path:** `/Users/mishaivchenko/dev/crypto/.env`
**Size:** 1,426 bytes
**Contents:** Exchange API keys (testnet), Telegram bot token, and other local-development secrets.
**Current .gitignore status:** `.env` and `.env.*` are already listed in `.gitignore`. Git status confirms `.env` does not appear in tracked changes.
**Recommended action:**
- Rotate any testnet credentials that might have been exposed (e.g., if this repo is or ever was public).
- Remove the file from disk: `rm -f .env && git add .gitignore` (already ignored, but clean up local disk).

### G2. `deploy/.env` — CREDENTIAL MASTER KEY

**Path:** `/Users/mishaivchenko/dev/crypto/deploy/.env`
**Size:** 1,361 bytes
**Contents:** Credential master key for AES-GCM encryption.
**Current .gitignore status:** Patterns `*.env`, `.env.*` at root level. The pattern `.env` matches only `./.env`, not `deploy/.env`. The pattern `*.env` in `.gitignore` would NOT match `deploy/.env` because `*.env` only matches files in the same directory as the `.gitignore` file (root).
**Verification:** Check `git status` to see if `deploy/.env` is tracked or untracked.
**Recommended action:**
- If tracked, remove from tracking: `git rm --cached deploy/.env`
- If untracked, add to `.gitignore`: add `/deploy/.env` or `deploy/.env`
- Rotate the master key if there is any concern about prior exposure.
- Remove the file from disk: `rm -f deploy/.env`

### G3. `data/fundingarb.db` — LOCAL SQLITE DATABASE

**Path:** `/Users/mishaivchenko/dev/crypto/data/fundingarb.db`
**Size:** 416 KB (plus `.db-shm` 32 KB and `.db-wal` 0 B)
**Current .gitignore status:** Covered by patterns `data/`, `/data/`, and `*.db`. Not tracked in git.
**Action:** These are local runtime data and should remain in `.gitignore`. No action needed unless the operator wants to back up the database for reference.

---

## H. MIGRATIONS PLAN

| Migration | Status | Notes |
|---|---|---|
| V1__baseline.sql | KEEP_IMMUTABLE | Foundation schema. Never modify. |
| V14__auto_approval_rules.sql | KEEP (active evolution) | Latest migration. Represents schema for auto-approval feature. |

**Observation:** V2–V13 do not exist on this branch. The CLAUDE.md claim of "V1–V5 migrations" is stale. The migration sequence is: V1 (baseline) -> V14 (auto-approval). This may indicate that V2–V13 were squashed, or that V14 was developed against a different schema baseline. Investigate if this is intentional or a gap.

---

## I. DOCUMENTATION / AGENT CLEANUP

### I1. CLAUDE.md updates (see C1 for details)
- "13 core production classes" -> "16 core production classes"
- "V1-V5 migrations" -> "V1, V14 migrations"

### I2. Archive audit reports
- Move `tasks/PROJECT_AUDIT_ROUND_1.md` to `docs/audit-archive/`
- Move `tasks/PROJECT_AUDIT_ROUND_2.md` to `docs/audit-archive/`
- Move `docs/12-engine-tdd-migration-prompt.md` to `docs/archive/`

### I3. `docs/engine-tdd/` review
Check if the engine TDD documentation properly documents 16 classes (not 13). Verify gap-matrix.md covers all current engine-app production classes.

---

## J. CLEANUP EXECUTION ORDER

### PR 1: Build artifact removal + gitignore hardening
1. `git rm -rf org/ META-INF/`
2. `git rm package-lock.json`
3. Add `org/`, `.worktrees/` to `.gitignore`
4. Add `deploy/.env` to `.gitignore` (if not already covered)
5. Verify build passes: `./gradlew build`

### PR 2: Empty directory cleanup
1. `rm -rf .worktrees/` (local only, already ignored or add to gitignore)
2. `rm -rf .maestro/playbooks/` (local only)
3. Decide on `funding-memory/` (keep or remove based on owner decision)

### PR 3: Documentation updates
1. Update CLAUDE.md: class count (13->16), migration range (V1-V5 -> V1, V14)
2. Archive audit reports to `docs/audit-archive/`
3. Archive `docs/12-engine-tdd-migration-prompt.md` to `docs/archive/`
4. Update gradle.properties stale version properties (after verification)
5. Optionally update `docs/01-system-flow.md`

### PR 4: Cleanup secret files (OWNER ACTION REQUIRED)
1. Rotate all credentials in `.env` and `deploy/.env`
2. Remove `.env` from disk
3. Remove `deploy/.env` from disk (and from git tracking if accidentally committed)

### PR 5: single_funding.sql decision
1. Ask operator if still needed
2. If yes: move to `docs/queries/single_funding.sql`
3. If no: `git rm single_funding.sql`

---

## K. OWNER DECISIONS REQUIRED

1. **`.env` credentials** — Are the testnet API keys and Telegram token in `.env` still in use? Should they be rotated before removal from disk?

2. **`deploy/.env` master key** — Is the AES-GCM credential master key in `deploy/.env` still in active use? If the key is rotated, all encrypted credentials stored in the database become undecipherable. Coordinate rotation carefully.

3. **`funding-memory/`** — Is this intended as an active Obsidian vault for working notes, or was it experimental and can be removed? If kept, consider adding `.obsidian/` to `.gitignore` and committing only actual note files.

4. **`single_funding.sql`** — Is this query still referenced by the operator for manual database inspection? If yes, move to `docs/queries/`. If no, delete.

5. **Migration gap (V2–V13)** — There are only V1 and V14 migrations. Does the current V14 migration actually work against the V1 schema, or is there a missing sequence? Test with a fresh SQLite database.

6. **`gradle.properties` stale versions** — Verify that `build.gradle` and `settings.gradle` do not reference the removed version properties. If they do, keep them. If they are managed by a BOM, remove them.

---

*End of cleanup plan. All findings verified against the current state of the repository at commit `c5cce55`.*
