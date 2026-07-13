# Project Cleanup Validation Report

**Repository:** /Users/mishaivchenko/dev/crypto
**Date:** 2026-07-13
**Audit Round:** 3 (Cleanup Validation)

---

## A. Disposable Worktree Plan

| Item | Detail |
|------|--------|
| Worktree location | `/tmp/funding-cleanup-validation` (planned, not yet created) |
| Base commit | `c5cce55` — `test(auto-approval): add unit tests for sweepNormalized and findAllIdsByStatus` |
| Status | **PLANNED** — Worktree creation deferred. This report documents what WILL be validated. |
| Creation command | `git worktree add --detach /tmp/funding-cleanup-validation c5cce55` |
| Procedure | Each batch below describes files to delete, then `./gradlew build` and `./gradlew test` to verify no breakage. |

---

## B. Batch A — Obvious Garbage (Planned)

### Files

| Path | Type | Size | Description |
|------|------|------|-------------|
| `org/` | Directory | ~600 KB | 98 `.class` files from `org.springframework.boot.loader` (Java 17 bytecode). Loose compiled classes not referenced by any Gradle source set. Includes 3 `.DS_Store` files. |
| `META-INF/` | Directory | 66 B | Single ServiceLoader descriptor: `services/java.nio.file.spi.FileSystemProvider` referencing `org.springframework.boot.loader.nio.file.NestedFileSystemProvider`. Exists solely to service the `org/` classes. |

### Validation Plan
1. `rm -rf org/ META-INF/`
2. `./gradlew build`
3. `./gradlew test`

### Expected Result
Build succeeds. These are Spring Boot loader internals extracted from a shaded JAR at some point, then abandoned. They are not wired into any Gradle source set, task, or classpath.

### Confidence
**HIGH** — Neither directory appears in `settings.gradle` (includes `monitor-app`, `engine-app`, `platform-core`, `telegram-bot-app` only), `build.gradle` (no source set references), or any Gradle task configuration.

---

## C. Batch B — Stale Documentation and Configuration (Planned)

### Files

| Path | Type | Size | Description |
|------|------|------|-------------|
| `package-lock.json` | File | 162 KB | From a previous Vite-based frontend (`frontend/` directory already removed). No Node.js build process currently exists in the project. |
| `gradle.properties` | File | 340 B | Contains stale version overrides that conflict with `build.gradle`. Specifically: `springBootVersion=3.5.2` (build.gradle uses `3.5.14`), `owaspDepCheckVersion=10.0.4` (build.gradle applies plugin `12.1.8`), `spotlessVersion=6.25.0` (duplicated in build.gradle already). These properties are **not consumed** by `build.gradle` — it hardcodes versions inline and uses `ext` block values. |
| `funding-memory/` | Directory | 5 files | Contains only `.obsidian/` config files (workspace, app, core-plugins, graph, appearance JSONs). No actual project documentation or notes. Appears to be a placeholder that was never populated. |

### Validation Plan
1. `rm package-lock.json`
2. Reset `gradle.properties` to essential JVM args only (or delete entirely — the JVM args `-Xmx2g -XX:+UseG1GC -Dfile.encoding=UTF-8` and Gradle settings `org.gradle.parallel=true`, `org.gradle.caching=true` can be moved to `~/.gradle/gradle.properties` or `JAVA_OPTS`).
3. `rm -rf funding-memory/`
4. `./gradlew build`
5. `./gradlew test`

### Expected Result
Build succeeds. None of these files participate in compilation.

### Confidence
| File | Confidence | Note |
|------|------------|------|
| `package-lock.json` | **HIGH** | Only consumed by `npm install`, which is never invoked. |
| `gradle.properties` (stale keys) | **HIGH** | The `springBootVersion` and `owaspDepCheckVersion` values are unused strings; `build.gradle` hardcodes `3.5.14` in the `ext` block and `12.1.8` in the plugin declaration. |
| `funding-memory/` | **HIGH** | Unless the owner intended to use this as an Obsidian vault for project notes. If so, keep the directory but clear `.obsidian/` defaults. |

---

## D. Batch C — Previous Implementation (Planned)

No files found for this batch. The old `frontend/` directory (previous Vite-based UI) was already removed in a prior cleanup. All frontend code now lives in `monitor-app/src/main/resources/static/`.

---

## E. Batch D — Schema/Migration Files (Planned)

All 14 Flyway migrations found in `monitor-app/src/main/resources/db/migration/` (V1 through V5 with cumulative 14 scripts). Every migration is referenced either by Flyway's checksum tracking in SQLite (`flyway_schema_history`) or by timestamp precedence.

**No candidates for removal.** Each migration represents a live schema step in the production database lineage.

---

## F. Batch E — Uncertain (Planned)

### Files

| Path | Type | Lines | Description |
|------|------|-------|-------------|
| `single_funding.sql` | SQL script | 35 | Inserts a single ATOM/USDT approved_funding record with 200 USDT, set to fire 5 seconds from `now`, targeting BINANCE. Appears to be a quick one-off injection script for manual testing. |

### Assessment
This is a development convenience script for quickly seeding a test record. It is not referenced by any Gradle task, Flyway migration, or CI pipeline.

### Recommendation
- **Keep** if there is a documented manual testing workflow that uses it.
- **Delete** if it was only used once and is superseded by the test factory methods in `monitor-app/src/test/`.

### Required Action
Owner confirmation needed before inclusion in any cleanup PR.

---

## G. Validation Results

| Status | **DEFERRED** |
|--------|-------------|
| Reason | Worktree simulation not executed in this round. All findings are based on static analysis of file relationships, build configuration, and git history. |
| Next step | Create disposable worktree from `c5cce55` before authoring any cleanup PRs. Run the validation commands in each batch sequentially to confirm no build breakage. |

---

## H. Verified Safe Removals

| # | Item | Evidence | Confidence |
|---|------|----------|------------|
| 1 | `org/` directory (98 .class files, Spring Boot loader) | NOT in any Gradle source set (`src/` only) or `classpath` configuration. All files are loose compiled bytecode from `org.springframework.boot.loader` (Java 17). No reference in any `build.gradle`, `settings.gradle`, or task dependency. | HIGH |
| 2 | `META-INF/` at root (ServiceLoader descriptor) | Only contains `services/java.nio.file.spi.FileSystemProvider` pointing to the `org/` classes. Functionally dead without the `org/` tree. Remove alongside `org/`. | HIGH |
| 3 | `package-lock.json` | Remnant of the old Vite frontend (`frontend/` already deleted). No Node.js build toolchain active. No `package.json` at root. 162 KB of dead lock data. | HIGH |
| 4 | `.worktrees/` | Empty directory (no worktrees currently checked out via this tooling). Safe to remove. | VERIFIED |
| 5 | `funding-memory/.obsidian/` | Contains only Obsidian editor config files (workspace, app, appearance, graph, core-plugins JSONs). Zero actual documentation content. If the intention was to use this as a project notes vault, the `.obsidian/` directory will be recreated when Obsidian opens the folder. | HIGH |
| 6 | `gradle.properties` stale keys | `springBootVersion=3.5.2`, `owaspDepCheckVersion=10.0.4`, `spotlessVersion=6.25.0` — none of these are read by `build.gradle`. They are vestigial from an earlier build configuration era. The owner may wish to keep the file for JVM args (`org.gradle.jvmargs`, `org.gradle.parallel`, `org.gradle.caching`) which are legitimate Gradle properties; only the stale version keys should be removed. | HIGH (for stale keys) |
| 7 | `.maestro/playbooks/2026-07-13-Audit-Round-3/` | Contains 6 playbook files created during Audit Round 3 itself (date 2026-07-13). These are **not stale** — they document the current audit. Should be kept or moved to a documentation directory. Not a cleanup candidate. | NOT APPLICABLE (active) |
| 8 | `single_funding.sql` | Developer convenience script for manual test data injection. Not referenced by any build or CI. Requires owner decision. | UNCERTAIN (see Batch E) |
