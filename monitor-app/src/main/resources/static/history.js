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

export function deriveTradeHealth(trade) {
    if (!trade) {
        return { label: "unknown", tone: "muted", reason: "Нет данных" };
    }
    if (trade.state === "FAILED") {
        return { label: "failed", tone: "bad", reason: "Trade state failed" };
    }
    if (trade.state === "CANCELLED") {
        return { label: "cancelled", tone: "bad", reason: "Trade cancelled" };
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

export function filterHistoryTrades(trades, filters = {}) {
    return (trades ?? []).filter((trade) => {
        const health = deriveTradeHealth(trade);
        const symbol = String(trade.symbol ?? "").toLowerCase();
        const venue = String(trade.venue ?? "").toLowerCase();
        const state = String(trade.state ?? "");
        const fundingTime = trade.fundingTime ? new Date(trade.fundingTime).getTime() : null;

        if (filters.symbol && !symbol.includes(String(filters.symbol).trim().toLowerCase())) {
            return false;
        }
        if (filters.venue && !venue.includes(String(filters.venue).trim().toLowerCase())) {
            return false;
        }
        if (filters.state && filters.state !== state) {
            return false;
        }
        if (filters.health && filters.health !== health.label) {
            return false;
        }
        if (filters.dateFrom && fundingTime && fundingTime < new Date(filters.dateFrom).getTime()) {
            return false;
        }
        if (filters.dateTo && fundingTime && fundingTime > new Date(filters.dateTo).getTime()) {
            return false;
        }
        if (filters.onlyFailed && !["FAILED", "CANCELLED"].includes(state)) {
            return false;
        }
        if (filters.onlyManual && Number(trade.manualLatencyAdjustmentMs ?? 0) === 0) {
            return false;
        }
        return true;
    });
}

export function historyTradeRow(trade) {
    const health = deriveTradeHealth(trade);
    const attempts = Number(trade.entryAttemptCount ?? 1);
    const spacingMs = Number(trade.entrySpacingMs ?? 0);
    const manualAdjustment = Number(trade.manualLatencyAdjustmentMs ?? 0);

    return `
        <article class="history-row" data-open-history-trade="${escapeHtml(trade.id)}">
            <div class="history-row-main">
                <strong class="history-symbol">${escapeHtml(trade.symbol ?? `Trade #${trade.id}`)}</strong>
                <span class="history-venue">${escapeHtml(trade.venue ?? "venue —")}</span>
                <span class="history-time">funding ${formatInstant(trade.fundingTime)}</span>
                <span class="history-side">${escapeHtml(trade.intendedSide ?? "SHORT")}</span>
            </div>
            <div class="history-row-plan">
                <span>${formatNumber(attempts)} attempts / ${formatPlainMs(spacingMs)}</span>
                <span>trigger ${formatDurationMs(trade.effectiveEntryLatencyMs ?? 0)}</span>
                <span>manual ${formatSignedMs(manualAdjustment)}</span>
                ${formatBadge("trade", trade.state)}
                <span class="badge ${escapeHtml(health.tone)}">${escapeHtml(health.label)}</span>
            </div>
        </article>
    `;
}

export function attemptLadderMarkup(trade) {
    const attempts = buildAttemptPlan(trade);
    if (!attempts.length) {
        return emptyState("Attempt plan отсутствует.", "У prepared trade не задан planned entry.");
    }

    return `
        <div class="attempt-ladder">
            ${attempts.map((attempt) => `
                <article class="attempt-step">
                    <span class="attempt-number">#${attempt.attemptNumber}</span>
                    <div>
                        <strong>Target ${formatInstant(attempt.targetEntryAt)}</strong>
                        <p class="muted">Trigger ${formatInstant(attempt.triggerAt)} · offset ${formatPlainMs(attempt.offsetMs)} · lead ${formatDurationMs(attempt.effectiveLatencyMs)}</p>
                    </div>
                    <span class="badge neutral">planned</span>
                </article>
            `).join("")}
        </div>
    `;
}

export function latencyStripMarkup(trade) {
    const firstAttempt = buildAttemptPlan(trade)[0];
    if (!firstAttempt) {
        return emptyState("Latency strip недоступен.", "Нужен planned entry.");
    }

    return `
        <div class="latency-strip">
            <div class="latency-node is-known">
                <span>planned trigger</span>
                <strong>${formatInstant(firstAttempt.triggerAt)}</strong>
            </div>
            <div class="latency-line"></div>
            <div class="latency-node">
                <span>submitted</span>
                <strong>pending</strong>
            </div>
            <div class="latency-line"></div>
            <div class="latency-node">
                <span>ack</span>
                <strong>pending</strong>
            </div>
            <div class="latency-line"></div>
            <div class="latency-node">
                <span>filled</span>
                <strong>pending</strong>
            </div>
        </div>
    `;
}

export function tradeHistoryDetailMarkup({ trade, event, candidate, journal, attempts = [] }) {
    const health = deriveTradeHealth(trade);
    const sourceSymbol = candidate?.normalizedSymbol ?? candidate?.rawSymbol ?? trade.symbol ?? "—";

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
                ${metaRow("Measured latency", formatDurationMs(trade.measuredEntryLatencyMs))}
                ${metaRow("Manual adjustment", formatSignedMs(trade.manualLatencyAdjustmentMs ?? 0))}
                ${metaRow("Effective trigger lead", formatDurationMs(trade.effectiveEntryLatencyMs ?? 0))}
                ${metaRow("Arm source", escapeHtml(trade.armSource ?? "—"))}
                ${metaRow("Note", escapeHtml(trade.notes ?? "—"))}
            </div>
        `)}
        ${section("4. Attempts", `
            ${latencyStripMarkup(trade)}
            ${attemptLadderMarkup(trade)}
            ${attempts.length ? attempts.map((attempt) => `
                <div class="meta-row">
                    <span class="meta-label">#${escapeHtml(attempt.attemptNumber ?? "—")} · ${escapeHtml(attempt.status)}</span>
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

function formatPlainMs(value) {
    if (value === null || value === undefined) {
        return "—";
    }
    return `${formatNumber(value)} мс`;
}
