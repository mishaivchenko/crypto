import { api } from "../../api.js";
import { emptyState, tradeCard, wireOpenButtons } from "../shared.js";
import { t } from "../../i18n.js";

const OUTCOME_STATES = new Set(["CLOSED", "FAILED"]);

async function loadOutcomes(trades) {
    const closedTrades = trades.filter((tr) => OUTCOME_STATES.has(tr.state));
    if (!closedTrades.length) {
        return {};
    }
    const results = await Promise.allSettled(
        closedTrades.map((tr) => api.getTradeOutcome(tr.id).then((o) => ({ id: tr.id, outcome: o })))
    );
    return Object.fromEntries(
        results
            .filter((r) => r.status === "fulfilled" && r.value.outcome)
            .map((r) => [r.value.id, r.value.outcome])
    );
}

export function tradesListMarkup(trades = [], outcomes = {}) {
    return trades.length
        ? trades.map((tr) => tradeCard(tr, outcomes[tr.id] ?? null)).join("")
        : emptyState(t("empty_trades"), t("empty_trades_detail"));
}

export async function renderTrades({ nodes, trades, onOpenTrade }) {
    const outcomes = await loadOutcomes(trades);
    nodes.tradesList.innerHTML = tradesListMarkup(trades, outcomes);
    wireOpenButtons(nodes.tradesList, "[data-open-trade]", onOpenTrade);
}
