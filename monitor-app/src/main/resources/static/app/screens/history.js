import { filterHistoryTrades, historyTradeRow } from "../../history.js";
import { emptyState, formatNumber } from "../shared.js";

export function historyListMarkup({ trades = [], attemptsByTrade = {}, filters = {} }) {
    const filtered = filterHistoryTrades(trades, filters, attemptsByTrade);
    const listMarkup = filtered.length
        ? filtered.map((trade) => historyTradeRow(trade, attemptsByTrade[trade.id] ?? [])).join("")
        : emptyState("История сделок пуста.", "Когда появятся armed trades, здесь будет разбор Signal -> Decision -> Plan -> Attempts -> Outcome.");

    return {
        countLabel: `${formatNumber(filtered.length)} / ${formatNumber(trades.length)} trades`,
        listMarkup
    };
}

export function renderHistory({ nodes, trades, attemptsByTrade = {}, filters = {}, onOpenHistoryTrade }) {
    const rendered = historyListMarkup({ trades, attemptsByTrade, filters });
    nodes.historyCount.textContent = rendered.countLabel;
    nodes.historyList.innerHTML = rendered.listMarkup;

    nodes.historyList.querySelectorAll("[data-open-history-trade]").forEach((row) => {
        row.addEventListener("click", () => onOpenHistoryTrade(row.dataset.openHistoryTrade));
    });
}
