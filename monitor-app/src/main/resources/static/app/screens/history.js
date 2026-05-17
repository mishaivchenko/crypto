import { filterHistoryTrades, historyTradeRow } from "../../history.js";
import { emptyState, formatNumber } from "../shared.js";
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

export function renderHistory({ nodes, trades, attemptsByTrade = {}, outcomesByTrade = {}, filters = {}, onOpenHistoryTrade }) {
    const rendered = historyListMarkup({ trades, attemptsByTrade, outcomesByTrade, filters });
    nodes.historyCount.textContent = rendered.countLabel;
    nodes.historyList.innerHTML = rendered.listMarkup;

    nodes.historyList.querySelectorAll("[data-open-history-trade]").forEach((row) => {
        row.addEventListener("click", () => onOpenHistoryTrade(row.dataset.openHistoryTrade));
    });
}
