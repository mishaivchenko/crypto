import { api } from "../../api.js";
import { emptyState, tradeCard, wireOpenButtons } from "../shared.js";

const OUTCOME_STATES = new Set(["CLOSED", "FAILED"]);

async function loadOutcomes(trades) {
    const closedTrades = trades.filter((t) => OUTCOME_STATES.has(t.state));
    if (!closedTrades.length) {
        return {};
    }
    const results = await Promise.allSettled(
        closedTrades.map((t) => api.getTradeOutcome(t.id).then((o) => ({ id: t.id, outcome: o })))
    );
    return Object.fromEntries(
        results
            .filter((r) => r.status === "fulfilled" && r.value.outcome)
            .map((r) => [r.value.id, r.value.outcome])
    );
}

export function tradesListMarkup(trades = [], outcomes = {}) {
    return trades.length
        ? trades.map((t) => tradeCard(t, outcomes[t.id] ?? null)).join("")
        : emptyState("Prepared Trades пока нет.", "Arm Funding Event, чтобы создать первую подготовленную сделку.");
}

export async function renderTrades({ nodes, trades, onOpenTrade }) {
    const outcomes = await loadOutcomes(trades);
    nodes.tradesList.innerHTML = tradesListMarkup(trades, outcomes);
    wireOpenButtons(nodes.tradesList, "[data-open-trade]", onOpenTrade);
}
