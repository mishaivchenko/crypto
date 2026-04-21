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
    journalMarkup,
    metaRow,
    section
} from "./ui.js";

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
        return { code: "FAILED", tone: "bad", label: "Failed", reason: "Нет данных по сделке" };
    }

    const summary = summarizeAttempts(attempts);
    const fundingMs = resolveFundingMs(trade, event);
    const windowPassed = fundingMs !== null && fundingMs < Date.now();

    if (trade.state === "FAILED") {
        return { code: "FAILED", tone: "bad", label: "Failed", reason: "Trade state failed" };
    }
    if (trade.state === "CANCELLED") {
        return { code: "CANCELLED", tone: "bad", label: "Cancelled", reason: "Trade cancelled" };
    }
    if (trade.state === "CLOSED") {
        return { code: "CLOSED", tone: "muted", label: "Closed", reason: "Trade lifecycle завершён" };
    }
    if (trade.state === "OPEN") {
        return { code: "OPEN", tone: "good", label: "Open", reason: "Position уже открыта" };
    }
    if (trade.state === "EXIT_PENDING") {
        return { code: "EXIT_PENDING", tone: "warning", label: "Exit pending", reason: "Ждём exit-side действие" };
    }
    if (trade.state === "ENTRY_PENDING") {
        return { code: "ENTRY_PENDING", tone: "warning", label: "Entry pending", reason: "Trade ждёт окно входа" };
    }
    if (trade.state === "ENTRY_ATTEMPTED") {
        return { code: "ENTRY_ATTEMPTED", tone: "warning", label: "Entry attempted", reason: "Trade уже делала попытку входа" };
    }
    if (summary.total > 0 && summary.failed === summary.total) {
        return { code: "ATTEMPTS_FAILED", tone: "bad", label: "Attempts failed", reason: "Все записанные entry attempts завершились ошибкой" };
    }
    if (trade.state === "ARMED" && windowPassed && summary.total === 0) {
        return { code: "MISSED_WINDOW", tone: "warning", label: "Missed window", reason: "Funding window уже прошёл, а execution attempts не были записаны" };
    }
    return { code: "PREPARED", tone: "info", label: "Prepared", reason: "Trade ещё находится в armed/pre-entry состоянии" };
}

export function deriveTradeHealth(trade, attempts = [], event = null) {
    if (!trade) {
        return { label: "unknown", tone: "muted", reason: "Нет данных" };
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
        return { label: "manual override", tone: "warning", reason: "Есть ручная latency поправка" };
    }
    if (Number(trade.effectiveEntryLatencyMs ?? 0) > 250) {
        return { label: "latency watch", tone: "warning", reason: "Высокий effective trigger lead" };
    }
    if (Number(trade.entryAttemptCount ?? 1) > 1) {
        return { label: "burst plan", tone: "info", reason: "Несколько entry attempts" };
    }
    return { label: "clean", tone: "good", reason: "Без явных risk flags" };
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

export function historyTradeRow(trade, attempts = []) {
    const historyStage = deriveHistoryStage(trade, null, attempts);
    const health = deriveTradeHealth(trade, attempts);
    const summary = summarizeAttempts(attempts);
    const configuredAttempts = Number(trade.entryAttemptCount ?? 1);
    const spacingMs = Number(trade.entrySpacingMs ?? 0);
    const manualAdjustment = Number(trade.manualLatencyAdjustmentMs ?? 0);
    const attemptSummary = summary.total
        ? `${formatNumber(summary.failed)} fail / ${formatNumber(summary.total)} recorded`
        : "attempts not recorded";

    return `
        <article class="history-row" data-open-history-trade="${escapeHtml(trade.id)}">
            <div class="history-row-main">
                <strong class="history-symbol">${escapeHtml(trade.symbol ?? `Trade #${trade.id}`)}</strong>
                <span class="history-venue">${escapeHtml(trade.venue ?? "venue —")}</span>
                <span class="history-time">funding ${formatInstant(trade.fundingTime)}</span>
                <span class="history-side">${escapeHtml(trade.intendedSide ?? "SHORT")}</span>
            </div>
            <div class="history-row-plan">
                <span>${formatNumber(configuredAttempts)} attempts / ${formatPlainMs(spacingMs)}</span>
                <span>trigger ${formatDurationMs(trade.effectiveEntryLatencyMs ?? 0)}</span>
                <span>${escapeHtml(attemptSummary)}${summary.lastRecordedAt ? ` · last ${escapeHtml(formatInstant(summary.lastRecordedAt))}` : ""}</span>
                ${formatBadge("historyStage", historyStage.code)}
                <span class="badge ${escapeHtml(health.tone)}">${escapeHtml(health.label)}</span>
                <span>manual ${formatSignedMs(manualAdjustment)}</span>
            </div>
        </article>
    `;
}

export function attemptLadderMarkup(trade, attempts = []) {
    const plannedAttempts = buildAttemptPlan(trade);
    if (!plannedAttempts.length) {
        return emptyState("Attempt plan отсутствует.", "У prepared trade не задан planned entry.");
    }

    const recordedByNumber = new Map(normalizedAttempts(attempts).map((attempt) => [Number(attempt.attemptNumber), attempt]));

    return `
        <div class="attempt-ladder">
            ${plannedAttempts.map((attempt) => {
                const recorded = recordedByNumber.get(attempt.attemptNumber);
                const status = recorded?.status ?? "PLANNED";
                const helper = recorded
                    ? `${recorded.failureReason ?? "Execution attempt записан"} · recorded ${formatInstant(recorded.createdAt)}`
                    : `Trigger ${formatInstant(attempt.triggerAt)} · offset ${formatPlainMs(attempt.offsetMs)} · lead ${formatDurationMs(attempt.effectiveLatencyMs)}`;
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
        return emptyState("Latency strip недоступен.", "Нужен planned entry.");
    }

    const summary = summarizeAttempts(attempts);
    const hasFailuresOnly = summary.total > 0 && summary.failed === summary.total;
    const firstSubmittedAt = normalizedAttempts(attempts).find((attempt) => attempt.submittedAt)?.submittedAt ?? null;
    const firstFilledAt = normalizedAttempts(attempts).find((attempt) => attempt.status === "FILLED")?.exchangeTimestamp ?? null;

    return `
        <div class="latency-strip">
            <div class="latency-node is-known">
                <span>planned trigger</span>
                <strong>${formatInstant(firstAttempt.triggerAt)}</strong>
            </div>
            <div class="latency-line"></div>
            <div class="latency-node ${firstSubmittedAt ? "is-known" : ""}">
                <span>submitted</span>
                <strong>${firstSubmittedAt ? formatInstant(firstSubmittedAt) : "pending"}</strong>
            </div>
            <div class="latency-line"></div>
            <div class="latency-node ${hasFailuresOnly ? "is-known" : ""}">
                <span>ack / fail</span>
                <strong>${hasFailuresOnly ? "failed" : "pending"}</strong>
            </div>
            <div class="latency-line"></div>
            <div class="latency-node ${firstFilledAt ? "is-known" : ""}">
                <span>filled</span>
                <strong>${firstFilledAt ? formatInstant(firstFilledAt) : "not filled"}</strong>
            </div>
        </div>
    `;
}

export function tradeHistoryDetailMarkup({ trade, event, candidate, journal, attempts = [] }) {
    const historyStage = deriveHistoryStage(trade, event, attempts);
    const health = deriveTradeHealth(trade, attempts, event);
    const sourceSymbol = candidate?.normalizedSymbol ?? candidate?.rawSymbol ?? trade.symbol ?? "—";
    const summary = summarizeAttempts(attempts);

    return `
        ${section("Health", `
            <div class="history-health-card ${escapeHtml(health.tone)}">
                <strong>${escapeHtml(health.label)}</strong>
                <span>${escapeHtml(health.reason)}</span>
            </div>
        `)}
        ${section("1. Source", `
            <div class="meta-grid">
                ${metaRow("Source type", escapeHtml(candidate?.sourceType ?? event?.sourceType ?? "—"))}
                ${metaRow("Raw symbol", escapeHtml(candidate?.rawSymbol ?? "—"))}
                ${metaRow("Normalized", escapeHtml(sourceSymbol))}
                ${metaRow("Candidate", trade.signalCandidateId ? `#${trade.signalCandidateId}` : "manual")}
                ${metaRow("Detected", formatInstant(candidate?.detectedAt))}
                ${metaRow("Review note", escapeHtml(candidate?.reviewNotes ?? "—"))}
            </div>
        `)}
        ${section("2. Event", `
            <div class="meta-grid">
                ${metaRow("Funding Event", `#${trade.fundingEventId}`)}
                ${metaRow("History stage", formatBadge("historyStage", historyStage.code), historyStage.reason)}
                ${metaRow("Raw trade state", formatBadge("trade", trade.state))}
                ${metaRow("Venue", escapeHtml(trade.venue ?? event?.venue ?? "—"))}
                ${metaRow("Symbol", escapeHtml(trade.symbol ?? event?.symbol ?? "—"))}
                ${metaRow("Funding time", formatInstant(trade.fundingTime ?? event?.fundingTime), formatFundingCountdown(trade.fundingTime ?? event?.fundingTime))}
                ${metaRow("Funding rate", formatDecimal(event?.fundingRatePct, 6))}
                ${metaRow("Event status", event?.status ? formatBadge("event", event.status) : "—")}
            </div>
        `)}
        ${section("3. Plan", `
            <div class="meta-grid">
                ${metaRow("Side", escapeHtml(trade.intendedSide ?? "SHORT"))}
                ${metaRow("Notional", `${formatDecimal(trade.notionalUsd, 2)} USD`)}
                ${metaRow("Planned entry", formatInstant(trade.plannedEntryAt))}
                ${metaRow("Planned exit", formatInstant(trade.plannedExitAt))}
                ${metaRow("Entry attempts", formatNumber(trade.entryAttemptCount ?? 1), `spacing ${formatPlainMs(trade.entrySpacingMs ?? 0)}`)}
                ${metaRow("Attempts recorded", formatNumber(summary.total))}
                ${metaRow("Failed attempts", formatNumber(summary.failed))}
                ${metaRow("Measured latency", formatDurationMs(trade.measuredEntryLatencyMs))}
                ${metaRow("Manual adjustment", formatSignedMs(trade.manualLatencyAdjustmentMs ?? 0))}
                ${metaRow("Effective trigger lead", formatDurationMs(trade.effectiveEntryLatencyMs ?? 0))}
                ${metaRow("Arm source", escapeHtml(trade.armSource ?? "—"))}
                ${metaRow("Note", escapeHtml(trade.notes ?? "—"))}
            </div>
        `)}
        ${section("4. Attempts", `
            ${latencyStripMarkup(trade, attempts)}
            ${attemptLadderMarkup(trade, attempts)}
            ${attempts.length ? attempts.map((attempt) => `
                <div class="meta-row">
                    <span class="meta-label">#${escapeHtml(attempt.attemptNumber ?? "—")} · ${formatBadge("orderAttempt", attempt.status)}</span>
                    <strong class="meta-value">${escapeHtml(attempt.symbol ?? trade.symbol ?? "—")}</strong>
                    <span class="meta-helper">${escapeHtml(attempt.failureReason ?? "Без ошибки")} · trigger ${formatInstant(attempt.triggerAt)} · recorded ${formatInstant(attempt.createdAt)}</span>
                </div>
            `).join("") : `
                <div class="empty-state compact">
                    <strong>Execution attempts пока нет.</strong>
                    <p>Запусти engine run-once, чтобы здесь появились FAILED/SUBMITTED attempts.</p>
                </div>
            `}
        `)}
        ${section("5. Position", `
            <div class="empty-state compact">
                <strong>Position ещё не открывалась в новом домене.</strong>
                <p>Этот блок ждёт реальный execution loop: entry price, qty, close timestamps и fees.</p>
            </div>
        `)}
        ${section("6. Outcome", `
            <div class="empty-state compact">
                <strong>Outcome пока не рассчитан.</strong>
                <p>Здесь будет net PnL, slippage, capture quality и итоговый код сделки.</p>
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
    return `${formatNumber(value)} мс`;
}
