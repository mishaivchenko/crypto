import { emptyState, tradeCard, wireOpenButtons } from "../shared.js";

export function tradesListMarkup(trades = []) {
    return trades.length
        ? trades.map(tradeCard).join("")
        : emptyState("Prepared Trades пока нет.", "Arm Funding Event, чтобы создать первую подготовленную сделку.");
}

export function renderTrades({ nodes, trades, onOpenTrade }) {
    nodes.tradesList.innerHTML = tradesListMarkup(trades);
    wireOpenButtons(nodes.tradesList, "[data-open-trade]", onOpenTrade);
}
