import { filterHistoryTrades, historyTradeRow } from "../../history.js";
import { emptyState, formatNumber, optionalRequest, escapeHtml } from "../shared.js";
import { buildHistoryTradeDrawerContent } from "../workflows/history-detail.js";
import { api } from "../../api.js";
import { t } from "../../i18n.js";

function enrichmentHealthChip(trade) {
    const latMs = trade.effectiveEntryLatencyMs;
    const liqScore = trade.liquidityAssessment?.score ?? trade.liquidityScore ?? null;
    const highLatency = latMs != null && latMs > 600;
    const thinLiquidity = liqScore === "THIN";

    if (highLatency) {
        return `<span class="chip chip-bad" data-enrich-chip="${escapeHtml(String(trade.id))}">Latency: ${latMs}мс</span>`;
    }
    if (thinLiquidity) {
        return `<span class="chip chip-warning" data-enrich-chip="${escapeHtml(String(trade.id))}">Liquidity: THIN</span>`;
    }
    return `<span class="chip chip-good" data-enrich-chip="${escapeHtml(String(trade.id))}">Enrichment OK</span>`;
}

function historyRowWithEnrichment(trade, attempts, outcome) {
    const baseHtml = historyTradeRow(trade, attempts, outcome);
    const chip = enrichmentHealthChip(trade);
    return baseHtml.replace(
        /(<details class="card-expansion" data-lazy-history=)/,
        `<div class="chip-row" style="margin: 2px 0 4px">${chip}</div>\n$1`
    );
}

export function historyListMarkup({ trades = [], attemptsByTrade = {}, outcomesByTrade = {}, filters = {} }) {
    const filtered = filterHistoryTrades(trades, filters, attemptsByTrade);
    const listMarkup = filtered.length
        ? filtered.map((trade) => historyRowWithEnrichment(trade, attemptsByTrade[trade.id] ?? [], outcomesByTrade[trade.id] ?? null)).join("")
        : emptyState(t("empty_history"), t("empty_history_detail"));

    return {
        countLabel: `${formatNumber(filtered.length)} / ${formatNumber(trades.length)} trades`,
        listMarkup
    };
}

export function renderHistory({ nodes, trades, attemptsByTrade = {}, outcomesByTrade = {}, filters = {}, showError, onRefresh }) {
    const rendered = historyListMarkup({ trades, attemptsByTrade, outcomesByTrade, filters });
    nodes.historyCount.textContent = rendered.countLabel;
    nodes.historyList.innerHTML = rendered.listMarkup;

    if (!nodes.historyList._expansionWired) {
        nodes.historyList._expansionWired = true;
        wireHistoryExpansion(nodes.historyList, { attemptsByTrade, showError, onRefresh });
        wireEnrichmentChipClick(nodes.historyList);
    }
}

function wireEnrichmentChipClick(container) {
    container.addEventListener("click", (e) => {
        const chip = e.target.closest("[data-enrich-chip]");
        if (!chip) return;
        const tradeId = chip.dataset.enrichChip;
        const details = container.querySelector(`[data-lazy-history="${CSS.escape(tradeId)}"]`);
        if (details && !details.open) details.open = true;
    });
}

function wireHistoryExpansion(container, { attemptsByTrade, showError, onRefresh }) {
    container.addEventListener("toggle", async (e) => {
        const details = e.target.closest("[data-lazy-history]");
        if (!details || !details.open) return;

        const tradeId = details.dataset.lazyHistory;
        const contentEl = details.querySelector(".card-full-content");
        if (!contentEl || !contentEl.querySelector(".card-loading")) return;

        try {
            const trade = await api.getArmedTrade(tradeId);
            const [event, candidate, journal, position, outcome] = await Promise.all([
                api.getFundingEvent(trade.fundingEventId),
                trade.signalCandidateId ? optionalRequest(() => api.getCandidate(trade.signalCandidateId)) : Promise.resolve(null),
                api.listArmedTradeJournal(tradeId).catch(() => []),
                api.getTradePosition(tradeId).catch(() => null),
                api.getTradeOutcome(tradeId).catch(() => null)
            ]);
            const attempts = attemptsByTrade[tradeId] ?? await api.listOrderAttempts(tradeId).catch(() => []);

            contentEl.innerHTML = buildHistoryTradeDrawerContent({ trade, event, candidate, journal, attempts, position, outcome });

            wireCancelTrade(contentEl, { showError, onRefresh });
        } catch (err) {
            contentEl.innerHTML = `<p class="card-loading">${escapeHtml(err.message)}</p>`;
        }
    }, true);
}

function wireCancelTrade(container, { showError, onRefresh }) {
    const btn = container.querySelector("[data-cancel-trade]");
    if (!btn) return;
    btn.addEventListener("click", async () => {
        btn.disabled = true;
        btn.textContent = t("trade_cancelling");
        try {
            await api.cancelArmedTrade(btn.dataset.cancelTrade);
            if (onRefresh) await onRefresh();
        } catch (err) {
            btn.disabled = false;
            btn.textContent = t("trade_cancel_button");
            showError(err.message);
        }
    });
}
