import { filterHistoryTrades, historyTradeRow } from "../../history.js";
import { emptyState, formatNumber, optionalRequest } from "../shared.js";
import { buildHistoryTradeDrawerContent } from "../workflows/history-detail.js";
import { api } from "../../api.js";
import { t } from "../../i18n.js";

export function historyListMarkup({ trades = [], attemptsByTrade = {}, outcomesByTrade = {}, filters = {} }) {
    const filtered = filterHistoryTrades(trades, filters, attemptsByTrade);
    const listMarkup = filtered.length
        ? filtered.map((trade) => historyTradeRow(trade, attemptsByTrade[trade.id] ?? [], outcomesByTrade[trade.id] ?? null)).join("")
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

    wireHistoryExpansion(nodes.historyList, { attemptsByTrade, showError, onRefresh });
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
            contentEl.innerHTML = `<p class="card-loading">${err.message}</p>`;
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
