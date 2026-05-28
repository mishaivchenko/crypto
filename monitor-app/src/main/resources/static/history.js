import {
    emptyState,
    escapeHtml,
    formatBadge,
    formatDecimal,
    formatDurationMs,
    formatFundingCountdown,
    formatInstant,
    formatNumber,
    formatSignedMs,
    formatTimeMs,
    journalMarkup,
    metaRow,
    section
} from "./ui.js";
import { modeLabel, sideLabel, venueIcon } from "./app/shared.js";
import { t } from "./i18n.js";

const FAILURE_ATTEMPT_STATUSES = new Set(["CANCELLED", "REJECTED", "FAILED", "EXPIRED"]);
const ACTIVE_ATTEMPT_STATUSES = new Set(["CREATED", "SUBMITTED", "ACKNOWLEDGED"]);

export function buildAttemptPlan(trade) {
    if (!trade?.plannedEntryAt) {
        return [];
    }
    const attempts = Math.max(1, Number(trade.entryAttemptCount ?? 1));
    const spacingMs = Math.max(0, Number(trade.entrySpacingMs ?? 0));
    const effectiveLatencyMs = Math.max(0, Number(trade.effectiveEntryLatencyMs ?? 0));
    const plannedEntryMs = new Date(trade.plannedEntryAt).getTime();

    return Array.from({ length: attempts }, (_, index) => {
        const offsetMs = spacingMs * index;
        const targetMs = plannedEntryMs + offsetMs;
        return {
            attemptNumber: index + 1,
            offsetMs,
            targetEntryAt: new Date(targetMs).toISOString(),
            triggerAt: new Date(targetMs - effectiveLatencyMs).toISOString(),
            effectiveLatencyMs,
            status: "PLANNED"
        };
    });
}

export function deriveHistoryStage(trade, event = null, attempts = []) {
    if (!trade) {
        return { code: "FAILED", tone: "bad", label: t("status_historyStage_FAILED"), reason: t("history_derive_no_data") };
    }

    const summary = summarizeAttempts(attempts);
    const fundingMs = resolveFundingMs(trade, event);
    const windowPassed = fundingMs !== null && fundingMs < Date.now();

    if (trade.state === "FAILED") {
        return { code: "FAILED", tone: "bad", label: t("status_historyStage_FAILED"), reason: "Trade state failed" };
    }
    if (trade.state === "CANCELLED") {
        return { code: "CANCELLED", tone: "bad", label: t("status_historyStage_CANCELLED"), reason: "Trade cancelled" };
    }
    if (trade.state === "CLOSED") {
        return { code: "CLOSED", tone: "muted", label: t("status_historyStage_CLOSED"), reason: t("history_derive_lifecycle_complete") };
    }
    if (trade.state === "OPEN") {
        return { code: "OPEN", tone: "good", label: t("status_historyStage_OPEN"), reason: t("history_derive_position_open") };
    }
    if (trade.state === "EXIT_PENDING") {
        return { code: "EXIT_PENDING", tone: "warning", label: t("status_historyStage_EXIT_PENDING"), reason: t("history_derive_exit_pending") };
    }
    if (trade.state === "ENTRY_PENDING") {
        return { code: "ENTRY_PENDING", tone: "warning", label: t("status_historyStage_ENTRY_PENDING"), reason: t("history_derive_entry_pending") };
    }
    if (trade.state === "ENTRY_ATTEMPTED") {
        return { code: "ENTRY_ATTEMPTED", tone: "warning", label: t("status_historyStage_ENTRY_ATTEMPTED"), reason: t("history_derive_entry_attempted") };
    }
    if (summary.total > 0 && summary.failed === summary.total) {
        return { code: "ATTEMPTS_FAILED", tone: "bad", label: t("status_historyStage_ATTEMPTS_FAILED"), reason: t("history_derive_attempts_failed") };
    }
    if (trade.state === "ARMED" && windowPassed && summary.total === 0) {
        return { code: "MISSED_WINDOW", tone: "warning", label: t("status_historyStage_MISSED_WINDOW"), reason: t("history_derive_missed") };
    }
    return { code: "PREPARED", tone: "info", label: t("status_historyStage_PREPARED"), reason: t("history_derive_prepared") };
}

export function deriveTradeHealth(trade, attempts = [], event = null) {
    if (!trade) {
        return { label: "unknown", tone: "muted", reason: t("history_derive_unknown") };
    }

    const historyStage = deriveHistoryStage(trade, event, attempts);
    if (historyStage.code === "FAILED" || historyStage.code === "ATTEMPTS_FAILED") {
        return { label: "failed", tone: "bad", reason: historyStage.reason };
    }
    if (historyStage.code === "CANCELLED") {
        return { label: "cancelled", tone: "bad", reason: historyStage.reason };
    }
    if (historyStage.code === "MISSED_WINDOW") {
        return { label: "missed window", tone: "warning", reason: historyStage.reason };
    }
    if (Number(trade.manualLatencyAdjustmentMs ?? 0) !== 0) {
        return { label: "manual override", tone: "warning", reason: t("history_health_manual_override") };
    }
    if (Number(trade.effectiveEntryLatencyMs ?? 0) > 250) {
        return { label: "latency watch", tone: "warning", reason: t("history_health_high_latency") };
    }
    if (Number(trade.entryAttemptCount ?? 1) > 1) {
        return { label: "burst plan", tone: "info", reason: t("history_health_multiple_attempts") };
    }
    return { label: "clean", tone: "good", reason: t("history_health_no_manual_flags") };
}

export function filterHistoryTrades(trades, filters = {}, attemptsByTrade = {}) {
    const { fromMs, toMs } = normalizedDateRange(filters.dateFrom, filters.dateTo);

    return (trades ?? []).filter((trade) => {
        const attempts = attemptsForTrade(attemptsByTrade, trade.id);
        const historyStage = deriveHistoryStage(trade, null, attempts);
        const health = deriveTradeHealth(trade, attempts);
        const symbol = String(trade.symbol ?? "").toLowerCase();
        const venue = String(trade.venue ?? "").toLowerCase();
        const fundingTime = trade.fundingTime ? new Date(trade.fundingTime).getTime() : null;

        if (filters.symbol && !symbol.includes(String(filters.symbol).trim().toLowerCase())) {
            return false;
        }
        if (filters.venue && !venue.includes(String(filters.venue).trim().toLowerCase())) {
            return false;
        }
        if (filters.state && filters.state !== historyStage.code) {
            return false;
        }
        if (filters.health && filters.health !== health.label) {
            return false;
        }
        if (fromMs !== null && fundingTime !== null && fundingTime < fromMs) {
            return false;
        }
        if (toMs !== null && fundingTime !== null && fundingTime > toMs) {
            return false;
        }
        if (filters.onlyFailed && !["FAILED", "CANCELLED", "ATTEMPTS_FAILED"].includes(historyStage.code)) {
            return false;
        }
        if (filters.onlyManual && Number(trade.manualLatencyAdjustmentMs ?? 0) === 0) {
            return false;
        }
        return true;
    });
}

export function historyTradeRow(trade, attempts = [], outcome = null) {
    const historyStage = deriveHistoryStage(trade, null, attempts);
    const health = deriveTradeHealth(trade, attempts);
    const isTestnet = trade.mode === "testnet" || (!trade.mode && String(trade.notes ?? "").includes("DEV_TEST_RUN"));
    const configuredAttempts = Number(trade.entryAttemptCount ?? 1);
    const spacingMs = Number(trade.entrySpacingMs ?? 0);
    const manualAdjustment = Number(trade.manualLatencyAdjustmentMs ?? 0);
    const effLat = trade.effectiveEntryLatencyMs ?? trade.measuredEntryLatencyMs;

    const net = outcome?.netPnlUsd != null ? Number(outcome.netPnlUsd) : null;
    const pnlChip = net != null
        ? `<span class="chip chip-${net >= 0 ? "good" : "bad"}" title="Net PnL">${net >= 0 ? "+" : ""}${formatDecimal(net, 2)} USD</span>`
        : "";

    const notionalChip = trade.notionalUsd != null
        ? `<span class="chip chip-muted" title="${t("trade_notional")}">${formatDecimal(trade.notionalUsd, 2)} USD</span>`
        : "";

    const countdown = formatFundingCountdown(trade.fundingTime);
    const countdownChip = `<span class="chip chip-muted">${countdown}</span>`;

    const sideChip = `<span class="chip chip-muted" title="${t("trade_side")}">${escapeHtml(sideLabel(trade.intendedSide))}</span>`;

    const attemptsChip = `<span class="chip chip-muted" title="${t("trade_entry_attempts")}">${formatNumber(configuredAttempts)}x · ${formatPlainMs(spacingMs)}</span>`;

    const latChip = effLat != null
        ? `<span class="chip chip-muted" title="${t("trade_effective_trigger")}">${effLat}ms</span>`
        : "";

    const manualChip = manualAdjustment !== 0
        ? `<span class="chip chip-warning" title="${t("history_manual_adj")}">adj ${formatSignedMs(manualAdjustment)}</span>`
        : "";

    const testnetBadge = isTestnet ? formatBadge("venue", t("label_testnet"), "info") : "";
    const healthChip = `<span class="chip chip-${escapeHtml(health.tone)}">${escapeHtml(health.label)}</span>`;

    return `
        <article class="list-item history-card" data-open-history-trade="${escapeHtml(trade.id)}">
            <header>
                <div>
                    <h3 class="item-title">${venueIcon(trade.venue)}${escapeHtml(trade.symbol ?? `${t("card_trade_prefix")}${trade.id}`)}</h3>
                    <p class="muted">${escapeHtml(trade.venue ?? "—")} · ${t("label_funding")} ${formatInstant(trade.fundingTime)}</p>
                </div>
                <div class="actions">
                    ${formatBadge("historyStage", historyStage.code)}
                    ${healthChip}
                    ${testnetBadge}
                    <button class="button secondary" type="button" data-open-history-trade="${escapeHtml(trade.id)}">${t("label_inspect")}</button>
                </div>
            </header>
            <div class="chip-row">
                ${pnlChip}
                ${notionalChip}
                ${sideChip}
                ${countdownChip}
                ${attemptsChip}
                ${latChip}
                ${manualChip}
            </div>
        </article>
    `;
}

export function attemptLadderMarkup(trade, attempts = []) {
    const plannedAttempts = buildAttemptPlan(trade);
    if (!plannedAttempts.length) {
        return emptyState(t("empty_attempt_plan"), t("empty_attempt_plan_detail"));
    }

    const recordedByNumber = new Map(normalizedAttempts(attempts).map((attempt) => [Number(attempt.attemptNumber), attempt]));

    return `
        <div class="attempt-ladder">
            ${plannedAttempts.map((attempt) => {
                const recorded = recordedByNumber.get(attempt.attemptNumber);
                const status = recorded?.status ?? "PLANNED";
                const reqDuration = recorded?.requestDurationMs != null ? ` · ${t("history_exchange_req")} ${formatDurationMs(recorded.requestDurationMs)}` : "";
                const helper = recorded
                    ? `${recorded.failureReason ?? t("trade_no_error")} · ${t("trade_recorded")} ${formatInstant(recorded.createdAt)}${reqDuration}`
                    : `${t("history_trigger_step")} ${formatInstant(attempt.triggerAt)} · offset ${formatPlainMs(attempt.offsetMs)} · lead ${formatDurationMs(attempt.effectiveLatencyMs)}`;
                return `
                    <article class="attempt-step">
                        <span class="attempt-number">#${attempt.attemptNumber}</span>
                        <div>
                            <strong>Target ${formatInstant(attempt.targetEntryAt)}</strong>
                            <p class="muted">${escapeHtml(helper)}</p>
                        </div>
                        ${formatBadge("orderAttempt", status)}
                    </article>
                `;
            }).join("")}
        </div>
    `;
}

export function latencyStripMarkup(trade, attempts = []) {
    const firstAttempt = buildAttemptPlan(trade)[0];
    if (!firstAttempt) {
        return emptyState(t("empty_latency_strip"), t("empty_latency_strip_detail"));
    }

    const summary = summarizeAttempts(attempts);
    const hasFailuresOnly = summary.total > 0 && summary.failed === summary.total;
    const normalAttempts = normalizedAttempts(attempts);
    const firstSubmittedAt = normalAttempts.find((a) => a.submittedAt)?.submittedAt ?? null;
    const firstFilledAt = normalAttempts.find((a) => a.status === "FILLED")?.exchangeTimestamp ?? null;
    const requestDurationMs = normalAttempts.find((a) => a.requestDurationMs != null)?.requestDurationMs ?? null;

    return `
        <div class="latency-strip">
            <div class="latency-node is-known">
                <span>${t("history_planned_trigger")}</span>
                <strong>${formatInstant(firstAttempt.triggerAt)}</strong>
            </div>
            <div class="latency-line"></div>
            <div class="latency-node ${firstSubmittedAt ? "is-known" : ""}">
                <span>${t("history_submitted_step")}</span>
                <strong>${firstSubmittedAt ? formatInstant(firstSubmittedAt) : t("history_pending")}</strong>
            </div>
            <div class="latency-line"></div>
            <div class="latency-node ${requestDurationMs != null ? "is-known" : ""}">
                <span>${t("history_exchange_req")}</span>
                <strong>${requestDurationMs != null ? formatDurationMs(requestDurationMs) : "—"}</strong>
            </div>
            <div class="latency-line"></div>
            <div class="latency-node ${hasFailuresOnly ? "is-known" : ""}">
                <span>${t("history_ack_fail")}</span>
                <strong>${hasFailuresOnly ? t("history_failed_state") : t("history_pending")}</strong>
            </div>
            <div class="latency-line"></div>
            <div class="latency-node ${firstFilledAt ? "is-known" : ""}">
                <span>${t("history_filled_step")}</span>
                <strong>${firstFilledAt ? formatInstant(firstFilledAt) : t("history_not_filled")}</strong>
            </div>
        </div>
    `;
}

export function exitTimingStripMarkup(trade, attempts = []) {
    if (!trade?.plannedExitAt) {
        return "";
    }
    const normalAttempts = normalizedAttempts(attempts);
    const exitAttempt = normalAttempts.find((a) => a.attemptNumber == null);
    const exitSubmittedAt = exitAttempt?.submittedAt ?? null;
    const exitFilledAt = exitAttempt?.status === "FILLED" || exitAttempt?.status === "ACKNOWLEDGED"
        ? (exitAttempt?.exchangeTimestamp ?? exitAttempt?.submittedAt ?? null)
        : null;

    return `
        <div class="latency-strip">
            <div class="latency-node is-known">
                <span>${t("history_planned_exit_label")}</span>
                <strong>${formatInstant(trade.plannedExitAt)}</strong>
            </div>
            <div class="latency-line"></div>
            <div class="latency-node ${exitSubmittedAt ? "is-known" : ""}">
                <span>${t("history_submitted_step")}</span>
                <strong>${exitSubmittedAt ? formatInstant(exitSubmittedAt) : t("history_pending")}</strong>
            </div>
            <div class="latency-line"></div>
            <div class="latency-node ${exitFilledAt ? "is-known" : ""}">
                <span>${t("history_filled_step")}</span>
                <strong>${exitFilledAt ? formatInstant(exitFilledAt) : t("history_not_filled")}</strong>
            </div>
        </div>
    `;
}

export function lifecycleTimelineMarkup(trade, event, candidate, attempts = [], position = null) {
    const normalAttempts = normalizedAttempts(attempts);
    const entryAttempts = normalAttempts.filter((a) => a.attemptNumber != null);
    const exitAttempt = normalAttempts.find((a) => a.attemptNumber == null);
    const plannedAttempts = buildAttemptPlan(trade);

    const phases = [];

    if (candidate?.detectedAt) {
        phases.push({ type: "milestone", label: t("history_signal_detected"), ts: candidate.detectedAt, variant: "known" });
    }
    if (trade.armedAt) {
        phases.push({ type: "milestone", label: t("history_armed"), ts: trade.armedAt, variant: "known" });
    }
    if (trade.plannedEntryAt || entryAttempts.length > 0) {
        const mappedAttempts = entryAttempts.map((a) => {
            const planned = plannedAttempts.find((p) => p.attemptNumber === Number(a.attemptNumber));
            const filledAt = (a.status === "FILLED" || a.status === "ACKNOWLEDGED") ? (a.exchangeTimestamp ?? null) : null;
            return { number: a.attemptNumber, triggerAt: planned?.triggerAt ?? null, submittedAt: a.submittedAt ?? null, filledAt, status: a.status };
        });
        phases.push({ type: "window", label: t("history_entry_window"), plannedAt: trade.plannedEntryAt, attempts: mappedAttempts });
    }
    if (position?.openedAt) {
        phases.push({ type: "milestone", label: t("history_position_open"), ts: position.openedAt, variant: "highlight" });
    }
    if (trade.plannedExitAt || exitAttempt) {
        const exitFilledAt = (exitAttempt?.status === "FILLED" || exitAttempt?.status === "ACKNOWLEDGED") ? (exitAttempt?.exchangeTimestamp ?? null) : null;
        const exitAttempts = exitAttempt ? [{ number: null, triggerAt: null, submittedAt: exitAttempt.submittedAt ?? null, filledAt: exitFilledAt, status: exitAttempt.status }] : [];
        phases.push({ type: "window", label: t("history_exit_window"), plannedAt: trade.plannedExitAt, attempts: exitAttempts });
    }
    if (position?.closedAt) {
        phases.push({ type: "milestone", label: t("history_closed"), ts: position.closedAt, variant: "highlight" });
    }

    if (!phases.length) {
        return emptyState(t("empty_timeline"), t("empty_timeline_detail"));
    }

    let prevKnownMs = null;
    const segments = phases.map((phase, i) => {
        const isLast = i === phases.length - 1;
        if (phase.type === "milestone") {
            const tsMs = phase.ts ? new Date(phase.ts).getTime() : null;
            const known = tsMs !== null && Number.isFinite(tsMs);
            const deltaHtml = (known && prevKnownMs !== null)
                ? `<span class="phase-delta">${formatDeltaMs(tsMs - prevKnownMs)}</span>`
                : "";
            if (known) prevKnownMs = tsMs;
            const dotClass = phase.variant === "highlight" ? "is-highlight" : (known ? "is-known" : "");
            return `
                <div class="phase-segment">
                    <div class="phase-dot ${dotClass}"></div>
                    <div class="phase-header">
                        <span class="phase-label">${escapeHtml(phase.label)}</span>
                        <strong class="phase-time">${formatTimeMs(phase.ts)}</strong>
                        ${deltaHtml}
                    </div>
                    ${isLast ? "" : `<div class="phase-line"></div><div class="phase-detail"></div>`}
                </div>`;
        }
        return `
            <div class="phase-segment">
                <div class="phase-dot is-window"></div>
                <div class="phase-header">
                    <span class="phase-label">${escapeHtml(phase.label)}</span>
                    ${phase.plannedAt ? `<span class="phase-planned">${t("history_planned")} ${formatTimeMs(phase.plannedAt)}</span>` : ""}
                </div>
                ${isLast ? "" : `<div class="phase-line"></div>`}
                <div class="phase-detail">
                    ${phase.attempts.length ? phase.attempts.map((a) => attemptRowHtml(a)).join("") : `<span class="attempt-empty">${t("empty_no_attempts_yet")}</span>`}
                </div>
            </div>`;
    });

    return `<div class="phase-timeline">${segments.join("")}</div>`;
}

function attemptRowHtml({ number, triggerAt, submittedAt, filledAt, status }) {
    const latencyMs = triggerAt && filledAt ? new Date(filledAt).getTime() - new Date(triggerAt).getTime() : null;
    const latencyHtml = latencyMs !== null ? `<span class="attempt-delta">${formatDeltaMs(latencyMs)}</span>` : "";
    return `
        <div class="phase-attempt">
            <span class="attempt-badge">${number != null ? "#" + escapeHtml(String(number)) : t("history_exit_label")}</span>
            ${triggerAt ? `<span class="phase-step">${t("history_trigger_step")}</span><span class="attempt-time">${formatTimeMs(triggerAt)}</span><span class="attempt-arrow">→</span>` : ""}
            ${submittedAt ? `<span class="phase-step">${t("history_submitted_step")}</span><span class="attempt-time">${formatTimeMs(submittedAt)}</span>${filledAt ? `<span class="attempt-arrow">→</span>` : ""}` : ""}
            ${filledAt ? `<span class="phase-step is-filled">${t("history_filled_step")}</span><span class="attempt-time">${formatTimeMs(filledAt)}</span>` : ""}
            ${latencyHtml}
            ${formatBadge("orderAttempt", status)}
        </div>`;
}

function formatDeltaMs(ms) {
    if (ms < 0) return `${formatNumber(ms)}ms`;
    if (ms < 1000) return `+${formatNumber(ms)}ms`;
    const s = Math.floor(ms / 1000);
    const rem = ms % 1000;
    return `+${formatNumber(s)}s ${formatNumber(rem)}ms`;
}

export function tradeHistoryDetailMarkup({ trade, event, candidate, journal, attempts = [], position = null, outcome = null }) {
    const historyStage = deriveHistoryStage(trade, event, attempts);
    const health = deriveTradeHealth(trade, attempts, event);
    const sourceSymbol = candidate?.normalizedSymbol ?? candidate?.rawSymbol ?? trade.symbol ?? "—";
    const summary = summarizeAttempts(attempts);

    return `
        ${section(t("history_0_timeline"), lifecycleTimelineMarkup(trade, event, candidate, attempts, position))}
        ${section(t("history_health"), `
            <div class="history-health-card ${escapeHtml(health.tone)}">
                <strong>${escapeHtml(health.label)}</strong>
                <span>${escapeHtml(health.reason)}</span>
            </div>
        `)}
        ${section(t("history_1_source"), `
            <div class="meta-grid">
                ${metaRow(t("history_source_type"), escapeHtml(candidate?.sourceType ?? event?.sourceType ?? "—"))}
                ${metaRow(t("history_raw_symbol"), escapeHtml(candidate?.rawSymbol ?? "—"))}
                ${metaRow(t("history_normalized"), escapeHtml(sourceSymbol))}
                ${metaRow(t("history_candidate"), trade.signalCandidateId ? `#${trade.signalCandidateId}` : t("label_manual"))}
                ${metaRow(t("history_detected"), formatInstant(candidate?.detectedAt))}
                ${metaRow(t("history_review_note"), escapeHtml(candidate?.reviewNotes ?? "—"))}
            </div>
        `)}
        ${section(t("history_2_event"), `
            <div class="meta-grid">
                ${metaRow(t("history_funding_event"), `#${trade.fundingEventId}`)}
                ${metaRow(t("history_history_stage"), formatBadge("historyStage", historyStage.code), historyStage.reason)}
                ${metaRow(t("history_raw_state"), formatBadge("trade", trade.state))}
                ${metaRow(t("history_symbol"), escapeHtml(trade.venue ?? event?.venue ?? "—"))}
                ${metaRow(t("trade_mode"), trade.mode ? formatBadge("venue", modeLabel(trade.mode), trade.mode === "testnet" ? "info" : "bad") : "—")}
                ${metaRow(t("history_symbol"), escapeHtml(trade.symbol ?? event?.symbol ?? "—"))}
                ${metaRow(t("event_funding_time"), formatInstant(trade.fundingTime ?? event?.fundingTime), formatFundingCountdown(trade.fundingTime ?? event?.fundingTime))}
                ${metaRow(t("history_funding_rate"), formatDecimal(event?.fundingRatePct, 6))}
                ${metaRow(t("history_event_status"), event?.status ? formatBadge("event", event.status) : "—")}
            </div>
        `)}
        ${section(t("history_3_plan"), `
            <div class="meta-grid">
                ${metaRow(t("trade_side"), escapeHtml(trade.intendedSide ?? "SHORT"))}
                ${metaRow(t("trade_notional"), `${formatDecimal(trade.notionalUsd, 2)} USD`)}
                ${metaRow(t("history_planned_entry"), formatInstant(trade.plannedEntryAt))}
                ${metaRow(t("history_planned_exit"), formatInstant(trade.plannedExitAt))}
                ${metaRow(t("history_entry_attempts"), formatNumber(trade.entryAttemptCount ?? 1), `${t("history_spacing")} ${formatPlainMs(trade.entrySpacingMs ?? 0)}`)}
                ${metaRow(t("history_attempts_recorded"), formatNumber(summary.total))}
                ${metaRow(t("history_failed_attempts"), formatNumber(summary.failed))}
                ${metaRow(t("history_measured_latency"), formatDurationMs(trade.measuredEntryLatencyMs))}
                ${metaRow(t("history_manual_adj"), formatSignedMs(trade.manualLatencyAdjustmentMs ?? 0))}
                ${metaRow(t("history_effective_trigger"), formatDurationMs(trade.effectiveEntryLatencyMs ?? 0))}
                ${metaRow(t("history_arm_source"), escapeHtml(trade.armSource ?? "—"))}
                ${metaRow(t("history_note"), escapeHtml(trade.notes ?? "—"))}
            </div>
        `)}
        ${section(t("history_4_attempts"), `
            ${attemptLadderMarkup(trade, attempts)}
            ${attempts.length ? attempts.map((attempt) => `
                <div class="meta-row">
                    <span class="meta-label">#${escapeHtml(attempt.attemptNumber ?? "—")} · ${formatBadge("orderAttempt", attempt.status)}</span>
                    <strong class="meta-value">${escapeHtml(attempt.symbol ?? trade.symbol ?? "—")}</strong>
                    <span class="meta-helper">${escapeHtml(attempt.failureReason ?? t("trade_no_error"))} · ${t("trade_trigger")} ${formatInstant(attempt.triggerAt)} · ${t("trade_recorded")} ${formatInstant(attempt.createdAt)}</span>
                </div>
            `).join("") : `
                <div class="empty-state compact">
                    <strong>${t("empty_attempts")}</strong>
                    <p>${t("empty_attempts_detail")}</p>
                </div>
            `}
        `)}
        ${section(t("history_5_position"), position ? `
            <div class="meta-grid">
                ${metaRow(t("history_state_section"), formatBadge("position", position.state))}
                ${metaRow(t("history_quantity"), formatDecimal(position.quantity, 6))}
                ${metaRow(t("history_entry_price"), position.entryPrice != null ? formatDecimal(position.entryPrice, 6) : "—")}
                ${metaRow(t("history_exit_price"), position.exitPrice != null ? formatDecimal(position.exitPrice, 6) : "—")}
                ${metaRow(t("history_opened_at"), formatInstant(position.openedAt))}
                ${metaRow(t("history_closed_at"), position.closedAt ? formatInstant(position.closedAt) : "—")}
            </div>
        ` : `
            <div class="empty-state compact">
                <strong>${t("empty_position")}</strong>
                <p>${t("empty_position_detail")}</p>
            </div>
        `)}
        ${section(t("history_6_outcome"), outcome ? `
            <div class="meta-grid">
                ${metaRow(t("history_net_pnl"), formatBadge("outcome", (outcome.netPnlUsd >= 0 ? "+" : "") + formatDecimal(outcome.netPnlUsd, 4) + " USD", outcome.netPnlUsd >= 0 ? "good" : "bad"))}
                ${metaRow(t("history_gross_pnl"), formatDecimal(outcome.grossPnlUsd, 4) + " USD")}
                ${metaRow(t("history_fees"), outcome.feesUsd != null ? formatDecimal(outcome.feesUsd, 4) + " USD" : "—")}
                ${metaRow(t("history_code"), escapeHtml(outcome.outcomeCode ?? "—"))}
                ${metaRow(t("history_evaluated_at"), formatInstant(outcome.evaluatedAt))}
            </div>
        ` : `
            <div class="empty-state compact">
                <strong>${t("empty_outcome")}</strong>
                <p>${t("empty_outcome_detail")}</p>
            </div>
        `)}
        ${section("Journal", journalMarkup(journal))}
    `;
}

function attemptsForTrade(attemptsByTrade, tradeId) {
    if (!attemptsByTrade) {
        return [];
    }
    return normalizedAttempts(attemptsByTrade[tradeId] ?? attemptsByTrade[String(tradeId)] ?? []);
}

function summarizeAttempts(attempts) {
    const normalized = normalizedAttempts(attempts);
    const failed = normalized.filter((attempt) => FAILURE_ATTEMPT_STATUSES.has(String(attempt.status ?? "").toUpperCase())).length;
    const active = normalized.filter((attempt) => ACTIVE_ATTEMPT_STATUSES.has(String(attempt.status ?? "").toUpperCase())).length;
    const filled = normalized.filter((attempt) => String(attempt.status ?? "").toUpperCase() === "FILLED").length;
    const firstSubmittedAt = normalized.find((attempt) => attempt.submittedAt)?.submittedAt ?? null;
    const lastRecordedAt = normalized.at(-1)?.createdAt ?? normalized.at(-1)?.updatedAt ?? null;

    return {
        total: normalized.length,
        failed,
        active,
        filled,
        firstSubmittedAt,
        lastRecordedAt
    };
}

function normalizedAttempts(attempts) {
    return [...(attempts ?? [])].sort((left, right) => {
        const leftOrder = Number(left?.attemptNumber ?? 0);
        const rightOrder = Number(right?.attemptNumber ?? 0);
        return leftOrder - rightOrder;
    });
}

function normalizedDateRange(dateFrom, dateTo) {
    const fromMs = dateFrom ? new Date(dateFrom).getTime() : null;
    const toMs = dateTo ? new Date(dateTo).getTime() : null;

    if (fromMs !== null && toMs !== null && Number.isFinite(fromMs) && Number.isFinite(toMs) && fromMs > toMs) {
        return { fromMs: toMs, toMs: fromMs };
    }
    return {
        fromMs: Number.isFinite(fromMs) ? fromMs : null,
        toMs: Number.isFinite(toMs) ? toMs : null
    };
}

function resolveFundingMs(trade, event) {
    const candidate = trade?.fundingTime ?? event?.fundingTime ?? null;
    if (!candidate) {
        return null;
    }
    const timestamp = new Date(candidate).getTime();
    return Number.isFinite(timestamp) ? timestamp : null;
}

function formatPlainMs(value) {
    if (value === null || value === undefined) {
        return "—";
    }
    return `${formatNumber(value)} ${t("unit_ms")}`;
}
