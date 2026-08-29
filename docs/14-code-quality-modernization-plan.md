# Code Quality Modernization Plan

Дата фиксации: 2026-08-29.

Этот документ фиксирует согласованный план модернизации качества кода. Цель не
в том, чтобы одним коммитом "очистить все", а в том, чтобы немедленно
остановить ухудшение и затем убирать накопленный долг измеримыми волнами.

## Guiding Principles

- DeepSeek review не является линтером и не принимает hard-gate решений.
  Он может объяснять падения CI, помогать приоритизировать исправления и
  давать второе мнение, но источник истины - детерминированные инструменты.
- Первым делом включается ratchet: новые нарушения не проходят, старый долг
  фиксируется отчетами и чистится отдельными refactoring waves.
- Форматирование и семантические изменения не смешиваются.
- Все важные правила выражаются числами, задачами Gradle и CI gates.
- Инструменты должны находить реальные баги: NPE, bad compiler patterns,
  bytecode-level defects, security/dataflow risks, unsafe architecture
  boundaries, dependency misuse.

## Current Codebase Snapshot

На момент планирования:

- Gradle: 9.1.0.
- Java toolchain: 25.
- CI main build: `./gradlew clean build`.
- Production Java files: 333.
- Production Java LOC: около 25 343.
- Module split:
  - `monitor-app`: 226 production Java files.
  - `platform-core`: 70 production Java files.
  - `telegram-bot-app`: 21 production Java files.
  - `engine-app`: 16 production Java files.
- Static UI:
  - 33 JS files.
  - 1 CSS file.
  - 1 HTML file.
  - около 11 747 LOC.
- Large current debt examples:
  - `engine-app/src/main/java/com/crypto/funding/engine/exchange/LiveExchangeExecutionPort.java`: 1047 LOC.
  - `engine-app/src/main/java/com/crypto/funding/engine/EngineExecutionService.java`: 852 LOC.
  - `monitor-app/src/main/resources/static/styles.css`: 2237 LOC.
  - `monitor-app/src/main/resources/static/i18n.js`: 1534 LOC.
- `platform-core` currently has no Spring/JPA/Hibernate/Flyway imports.
- Around 30 files in `monitor-app/api` and `monitor-app/application` import
  persistence directly; this is accepted as existing debt, not an immediate
  blocking rule.
- `.claude/worktrees/**` contains copied project trees, so all format/lint
  targets must explicitly exclude tool worktrees and build/cache folders.

## Tool Stack

### Immediate Blocking Tools

- Spotless for Java and repository text formatting.
- Palantir Java Format through Spotless.
- Prettier for static UI JS/CSS/HTML/tests.
- ESLint v9 flat config for critical JS correctness rules.
- ArchUnit for architecture rules that already reflect intended boundaries.

### Bug-Finding Static Analysis

- Error Prone for Java compile-time bug patterns.
- NullAway with JSpecify for staged null-safety.
- SpotBugs for bytecode-level defects.
- FindSecBugs for security/dataflow checks on Java web/crypto/HTTP code.
- Semgrep for custom project-specific bug patterns.
- Dependency Analysis Gradle Plugin for unused and mis-scoped dependencies.
- Qodana or Sonar as dashboard/manual/weekly dead-code and broad quality
  analysis.

### Remediation Tooling

- OpenRewrite is a remediation/refactoring tool, not an initial CI gate.
  It can be used for bulk cleanup recipes after reports identify a safe wave.

## Version Policy

Pinned current choices as of 2026-08-29:

- Spotless Gradle plugin: `8.10.0`.
- Error Prone Gradle plugin: `5.1.0`.
- Error Prone core: `2.50.0`.
- PMD: `7.24.0`.
- Dependency Analysis Gradle plugin: `3.19.1`.
- ArchUnit JUnit 5: `1.5.0`.
- SpotBugs Gradle plugin: latest compatible `6.5.x`.
- FindSecBugs: latest available `1.14.0`.

Formatter engine versions should be pinned explicitly where possible, especially
Palantir Java Format, so future dependency updates do not unexpectedly
reformat the repository.

## Repository Targeting Rules

All formatters and static analysis tasks must exclude:

- `.claude/**`
- `.agents/**`
- `.codex/**`
- `.gradle/**`
- `.maestro/**`
- `**/build/**`
- `**/node_modules/**`

Java Spotless targets should be explicit source roots, not global `**/*.java`:

- `platform-core/src/**/*.java`
- `monitor-app/src/**/*.java`
- `engine-app/src/**/*.java`
- `telegram-bot-app/src/**/*.java`

## Gradle Task Model

Add a root `quality` task for blocking fast checks:

- `spotlessCheck`
- `:monitor-app:frontendLint`
- `:monitor-app:frontendFormatCheck`

Add a root `qualityReport` task for report-only analysis:

- `pmdMain`
- `spotbugsMain`
- FindSecBugs via SpotBugs plugin.
- dependency analysis/build health.
- Semgrep if wired through CI rather than Gradle can remain outside this task.

`quality` should not run the full test suite. Architecture tests live in normal
JUnit tests and are caught by `build`. If a separate architecture source set is
needed later, add it in a later wave.

## CI/CD Model

Add a separate GitHub Actions job named `code_quality`.

Initial behavior:

- Runs in parallel with `build`.
- Runs blocking `./gradlew quality`.
- Runs report-only `./gradlew qualityReport` after blocking checks pass.
- Uploads PMD/SpotBugs/FindSecBugs/dependency-analysis artifacts.
- Does not use DeepSeek as a decision maker.

Deployment/Docker should depend on the blocking quality job once it exists.

DeepSeek failure analysis may read quality output only to explain and
prioritize, not to decide whether the code passes.

## JS/UI Tooling

JS tooling belongs in `monitor-app`, because the real static UI and existing
`frontendTest` live there.

Use npm, not pnpm/yarn, for the initial setup.

Add:

- `monitor-app/package.json` scripts:
  - `lint`
  - `format`
  - `format:check`
  - existing `test` wrapper around `node --test` may be added later.
- `monitor-app/package-lock.json`.
- `monitor-app/eslint.config.js`.
- `monitor-app/.prettierrc`.
- Optional `.prettierignore` if needed.

Prettier:

- Indentation: 4 spaces.
- `printWidth`: 120.
- Quotes: double quotes.
- Targets:
  - `src/main/resources/static/**/*.js`
  - `src/main/resources/static/**/*.css`
  - `src/main/resources/static/**/*.html`
  - `src/test/js/**/*.mjs`

ESLint:

- Use ESLint v9 flat config.
- Static UI files use browser globals.
- Test files use Node globals.
- Enabled immediately as blocking:
  - `no-undef`
  - `no-unused-vars`, with ignored names matching `^_`
  - `no-unreachable`
  - `no-redeclare`
- Do not make style rules compete with Prettier.
- Do not initially fail on `var`; this becomes a cleanup wave.
- Warnings budget for enabled blocking rules: 0.

CSS:

- Initially format with Prettier only.
- Do not add Stylelint in the first wave.

## Java Formatting

Spotless should:

- Keep existing misc formatting for Gradle, Markdown, YAML.
- Add Java formatting.
- Use Palantir Java Format.
- Run `removeUnusedImports()`.
- Run `forbidWildcardImports()`.
- Format production and test Java.
- Include Java migrations in `monitor-app/src/main/java/db/migration`.
- CI runs `spotlessCheck`, never `spotlessApply`.

The first implementation PR may contain format-only changes. Semantic cleanup
must not be mixed into that PR.

## Java Compiler And Error Prone

Error Prone:

- Apply to production `compileJava` first.
- Do not initially apply to `compileTestJava`.
- Enable default Error Prone ERROR checks as blocking.
- Do not promote all warnings to errors initially.
- Add a Gradle property escape hatch:
  - `-PerrorProneEnabled=false`

If Java 25/javac internals cause compatibility issues, preserve the plan and
temporarily disable Error Prone through the property while selecting a working
version combination.

Java compiler warnings:

- Add common `-Xlint:all,-processing` in a later step.
- Do not use `-Werror` globally at first.
- First candidates for `-Werror`:
  - `platform-core`
  - `engine-app`
- `monitor-app` gets `-Werror` only after noise is reduced.

## Null-Safety

Use NullAway through Error Prone with JSpecify annotations.

Policy:

- Prefer `org.jspecify.annotations.Nullable`.
- Introduce `@NullMarked` package by package.
- NullAway is blocking only for packages that are explicitly inside the
  null-safety perimeter.
- First pilot candidate: `platform-core`, because it is pure domain/contracts
  and has less Spring framework magic.
- Existing widespread `return null` patterns are treated as debt until a package
  is opted in.

## PMD

PMD is initially report-only.

Version:

- Use PMD `7.24.0`.

Reports:

- HTML.
- XML.

Scope:

- Start with `pmdMain`.
- Do not run `pmdTest` initially.

Rules:

- Best Practices.
- Error Prone.
- Performance.
- Design.
- Avoid style/naming-heavy rules that duplicate formatter concerns.
- Complexity rules are enabled report-only.

Initial fail policy:

- `ignoreFailures = true`.
- CI publishes reports.
- No hard fail by total count initially.

Ratchet target:

- First cleanup milestone: reduce PMD violations by 30%.
- Later hard target: 0 high-priority PMD violations.

## SpotBugs And FindSecBugs

SpotBugs:

- Initially report-only.
- Run `spotbugsMain`.
- Generate HTML and XML reports.
- Analyze all modules initially.
- If runtime or noise is excessive, scope down to `monitor-app` and
  `platform-core` first.

FindSecBugs:

- Add as a SpotBugs plugin.
- Initially report-only.
- Later fail on confirmed high-confidence/high-priority findings.

No baseline file is created in the first enablement PR. Baseline becomes useful
only when converting report-only findings into blocking gates.

## Dependency Analysis

Use the AutonomousApps Dependency Analysis Gradle plugin.

Initial mode:

- Report-only.
- Print build health to console.
- Publish report artifacts in CI.

Targets after review:

- 0 unused declared dependencies.
- 0 used transitive dependencies.
- 0 unused Gradle plugins.

Do not exclude Spring starters before seeing the first report. Starters may
create false positives, but the first report should reveal the actual shape of
the dependency graph.

## Semgrep

Add Semgrep as a real bug-finding layer, initially report-only/non-blocking or
manual.

Use:

- Community rules.
- A small custom project rule pack.

Initial custom rule ideas:

- Raw credentials must never be returned by API DTOs/controllers.
- `catch (Exception)` without logging/metric/explicit domain handling.
- `RestClient` or exchange HTTP calls inside `@Transactional` methods.
- `Thread.sleep` outside tests or explicit backoff components.
- `System.currentTimeMillis` in testable services without an injectable clock.
- `new RuntimeException` in application layer where domain-specific exceptions
  are expected.
- Unsafe crypto or hardcoded token/key patterns.

Semgrep findings become blocking only after triage and after false positives
are removed or suppressed.

## ArchUnit

ArchUnit tests live in existing test source sets:

- `platform-core/src/test/java/com/crypto/funding/architecture/PlatformCoreArchitectureTest.java`
- `monitor-app/src/test/java/com/crypto/funding/architecture/MonitorArchitectureTest.java`
- `engine-app/src/test/java/com/crypto/funding/engine/EngineArchitectureTest.java`
- `telegram-bot-app/src/test/java/com/crypto/funding/telegram/TelegramArchitectureTest.java`

ArchUnit is blocking immediately for rules that already represent intended,
mostly clean boundaries.

Initial blocking rules:

- `platform-core` must not depend on:
  - Spring.
  - JPA.
  - Hibernate.
  - Flyway.
  - monitor persistence.
- `platform-core` may depend on Jackson annotations.
- `engine-app` must not depend on:
  - JPA.
  - Hibernate.
  - Flyway.
  - monitor-app persistence internals.
- `engine-app` may use Spring Web, Actuator and `RestClient`.
- `telegram-bot-app` must not depend on monitor-app internals.
- `telegram-bot-app` may depend on `platform-core`.
- JPA entities must live in `monitor-app.infrastructure.persistence.model`.
- JPA repositories must live in `monitor-app.infrastructure.persistence.repository`.
- Persistence annotations and repositories must not be introduced outside the
  persistence package, except for temporarily accepted Criteria API usage in
  existing query services.
- Controllers should live in `..api..` or the owning runtime package where
  current engine controller layout requires it.

Not initially blocking:

- `monitor-app.application` importing persistence repositories/entities.
- `monitor-app.api` importing persistence internals.
- Package cycle checks.

Those become cleanup/refactoring waves.

## Numeric Policy

### Java Targets

- Ordinary class target: 300 LOC.
- Temporary JPA/DTO-heavy class allowance: 500 LOC.
- Method target: 30 LOC.
- Temporary max method size: 50 LOC.
- Constructor max: 30 LOC.
- Method parameter target: 7 production parameters.
- Test builders/fixtures temporary parameter allowance: 10.
- Class fields:
  - 15 report.
  - 25 temporary hard ceiling later.
  - JPA entities are temporarily exempt until cleanup.
- Public methods per class:
  - 20 report.
  - 30 future hard ceiling.

### Complexity Targets

- Cyclomatic complexity:
  - 10 report/warning.
  - 15 temporary hard fail after baseline cleanup.
- Cognitive complexity:
  - 15 report/warning.
  - 20 temporary hard fail after baseline cleanup.
  - Later target: 15.
- NPath complexity:
  - 200 report.
  - 400 temporary hard fail after baseline cleanup.
  - Later target: 200.

### Static UI Targets

- JS file target: 300 LOC.
- New JS file temporary hard ceiling: 500 LOC.
- CSS file target: 500 LOC.
- Existing `styles.css` remains a debt item, not an immediate blocker.
- Prettier violations: 0.
- ESLint critical violations: 0.

### Analysis Targets

- Spotless violations: 0 immediately.
- ArchUnit violations for selected rules: 0 immediately.
- Error Prone ERROR violations: 0 when enabled.
- NullAway violations: 0 inside opted-in `@NullMarked` packages.
- PMD high-priority violations: target 0 after cleanup.
- SpotBugs high-confidence/high-priority bugs: target 0 after triage.
- FindSecBugs confirmed high findings: target 0 after triage.
- Dependency Analysis actionable advice: target 0 after dependency cleanup.
- Confirmed unused production classes: target 0 after Qodana/Sonar triage.

## PR Plan

### PR 1: Formatting And Basic Hygiene

Goal: stop formatting drift.

Changes:

- Upgrade Spotless.
- Add Java Spotless with Palantir Java Format.
- Add `removeUnusedImports()` and `forbidWildcardImports()`.
- Add Prettier and ESLint under `monitor-app`.
- Add Gradle `frontendLint` and `frontendFormatCheck`.
- Add root `quality` task.
- Add documentation.
- Apply format-only changes.

Blocking:

- `spotlessCheck`.
- `frontendLint`.
- `frontendFormatCheck`.

No semantic refactoring in this PR.

### PR 2: Static Analysis Reports

Goal: get real reports without blocking delivery.

Changes:

- Add PMD report-only.
- Add SpotBugs report-only.
- Add FindSecBugs report-only.
- Add Dependency Analysis report-only.
- Add Semgrep skeleton and/or CI job.
- Add CI artifact uploads.
- Add `qualityReport`.

Blocking:

- None of the new semantic analyzers initially.

Output:

- HTML/XML reports.
- Build health/dependency advice.
- First quality debt snapshot.

### PR 3: Error Prone

Goal: compile-time real bug detection.

Changes:

- Add Error Prone Gradle plugin.
- Add Error Prone core dependency.
- Apply to production Java compilation.
- Add `-PerrorProneEnabled=false` escape hatch.

Blocking:

- Default Error Prone ERROR checks.

No warning-to-error policy initially.

### PR 4: Architecture And Null-Safety Pilot

Goal: enforce clean boundaries and start null-safety perimeter.

Changes:

- Add ArchUnit JUnit 5.
- Add initial architecture tests.
- Add JSpecify annotations dependency.
- Add NullAway pilot for selected package(s), starting with `platform-core`
  if feasible.

Blocking:

- ArchUnit selected rules.
- NullAway only for opted-in packages.

### PR 5+: Cleanup Waves

Each cleanup wave should be focused and reviewable.

Rules:

- Up to 1000 changed LOC per wave, except format-only PRs.
- One large class or one cohesive cluster per wave.
- Do not mix unrelated refactors.

Candidate waves:

- `LiveExchangeExecutionPort` split and clock/time handling.
- `EngineExecutionService` orchestration decomposition.
- `monitor-app` persistence boundary cleanup.
- Dependency cleanup after dependency-analysis report.
- Static UI decomposition.
- `styles.css` split.
- Replace broad `catch (Exception)` with typed handling.
- Reduce null-return APIs and expand JSpecify perimeter.
- Confirm and delete unused production classes from Qodana/Sonar reports.

## Gate Promotion Criteria

Report-only tools become blocking when:

- The report is clean, or
- A reviewed baseline exists, and
- The tool has passed on at least 2 consecutive PRs, and
- False positives have documented suppressions.

Specific promotion:

- PMD: after high-priority violations are 0 or baselined.
- SpotBugs: fail on high-confidence/high-priority findings first.
- FindSecBugs: fail on confirmed high findings first.
- Dependency Analysis: fail after Spring starter and transitive dependency
  advice is reviewed.
- Semgrep: fail only for triaged custom rules with low false-positive rate.
- NullAway: fail per `@NullMarked` package.
- `-Werror`: first `platform-core` and `engine-app`, then `monitor-app`.

## Definition Of Done

For ordinary PRs:

- `./gradlew quality`
- `./gradlew build`
- `npm --prefix monitor-app run lint`

If `engine-app` is touched:

- `./gradlew engineTddGate`

If dependency or security surface is touched:

- `./gradlew security` when appropriate.

CI is authoritative. Local hooks may help, but they do not replace CI.

## Hook Policy

Current local `.git/hooks/pre-commit` and `.git/hooks/pre-push` contain a
main-branch guard that defaults to `./gradlew test`.

Do not rewrite hooks in the first PR.

Recommended local override:

```bash
MAIN_HOOK_CHECK_CMD="./gradlew quality test"
```

If tracked hook management becomes useful later, prefer Lefthook over ad hoc
scripts. Do not run PIT, dependency security scan, or full release build in a
local pre-push hook by default.

## Documentation Updates

Add or update:

- `docs/code-quality.md` or this document as the canonical plan.
- `docs/README.md` index entry.
- `README.md` quick-start commands once tasks exist.
- `AGENTS.md` command list once tasks exist.
- `CLAUDE.md` command list once tasks exist.

Keep detailed policy in docs. Keep agent guidance short and operational.
