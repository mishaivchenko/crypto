import { api } from "../../api.js";
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
    section,
    sideLabel
} from "../shared.js";
import { buildDeleteCandidateSection } from "./pipeline.js";

export function buildTradeDrawerContent({ trade, journal, attempts }) {
    return `
        ${section("Trade parameters", `
            <div class="meta-grid">
                ${metaRow("Статус", formatBadge("trade", trade.state))}
                ${metaRow("Funding Event", `#${trade.fundingEventId}`)}
                ${metaRow("Source signal", trade.signalCandidateId ? `#${trade.signalCandidateId}` : "manual")}
                ${metaRow("Venue", escapeHtml(trade.venue ?? "—"))}
                ${metaRow("Instrument", escapeHtml(trade.symbol ?? "—"))}
                ${metaRow("Funding time", formatInstant(trade.fundingTime), formatFundingCountdown(trade.fundingTime))}
                ${metaRow("Notional", `${formatDecimal(trade.notionalUsd, 2)} USD`)}
                ${metaRow("Side", escapeHtml(sideLabel(trade.intendedSide)))}
                ${metaRow("Planned entry", formatInstant(trade.plannedEntryAt))}
                ${metaRow("Planned exit", formatInstant(trade.plannedExitAt))}
                ${metaRow("Entry attempts", formatNumber(trade.entryAttemptCount ?? 1), `spacing ${formatDurationMs(trade.entrySpacingMs ?? 0)}`)}
                ${metaRow("Measured latency", formatDurationMs(trade.measuredEntryLatencyMs))}
                ${metaRow("Manual latency adj", formatSignedMs(trade.manualLatencyAdjustmentMs ?? 0))}
                ${metaRow("Effective trigger lead", formatDurationMs(trade.effectiveEntryLatencyMs ?? 0))}
                ${metaRow("Armed at", formatInstant(trade.armedAt))}
                ${metaRow("Entry lead", formatDurationMs(trade.entryLeadMs))}
                ${metaRow("Exit lead", formatDurationMs(trade.exitLeadMs))}
                ${metaRow("Arm source", escapeHtml(trade.armSource ?? "—"))}
                ${metaRow("Note", escapeHtml(trade.notes ?? "—"))}
            </div>
        `)}
        ${section("Execution attempts", attempts.length ? attempts.map((attempt) => `
            <div class="meta-row">
                <span class="meta-label">#${escapeHtml(attempt.attemptNumber ?? "—")} · ${escapeHtml(attempt.status)}</span>
                <strong class="meta-value">${escapeHtml(attempt.symbol)} ${escapeHtml(attempt.side)}</strong>
                <span class="meta-helper">${escapeHtml(attempt.failureReason ?? "Без ошибки")} · trigger ${formatInstant(attempt.triggerAt)} · recorded ${formatInstant(attempt.createdAt)}</span>
            </div>
        `).join("") : emptyState("Execution attempts пока нет.", "Запусти engine run-once, чтобы увидеть FAILED/SUBMITTED попытки."))}
        ${trade.signalCandidateId ? buildDeleteCandidateSection({ id: trade.signalCandidateId }, "Delete source signal") : ""}
        ${section("Journal", journalMarkup(journal))}
    `;
}

export async function openTradeDetail({ id, nodes, showError }) {
    try {
        const [trade, journal, attempts] = await Promise.all([
            api.getArmedTrade(id),
            api.listArmedTradeJournal(id),
            api.listOrderAttempts(id)
        ]);

        nodes.drawerType.textContent = "Prepared Trade";
        nodes.drawerTitle.textContent = trade.symbol ? `${trade.symbol} · ${trade.venue}` : `Сделка #${trade.id}`;
        nodes.drawerContent.innerHTML = buildTradeDrawerContent({ trade, journal, attempts });
    } catch (error) {
        showError(error.message);
    }
}
