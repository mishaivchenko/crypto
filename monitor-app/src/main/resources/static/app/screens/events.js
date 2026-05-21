import { api } from "../../api.js";
import { emptyState, eventCard, wireOpenButtons } from "../shared.js";
import { t } from "../../i18n.js";

export function eventsListMarkup(page = {}, { tradeMap = {}, outcomeMap = {} } = {}) {
    const events = page?.content ?? [];
    return events.length
        ? events.map((e) => eventCard(e, {
            trade: e.armedTradeId ? (tradeMap[e.armedTradeId] ?? null) : null,
            outcome: e.armedTradeId ? (outcomeMap[e.armedTradeId] ?? null) : null
          })).join("")
        : emptyState(t("empty_events"), t("empty_events_detail"));
}

export async function renderFundingEvents({ nodes, page, onOpenEvent }) {
    const events = page?.content ?? [];

    const armedTradeIds = events.map((e) => e.armedTradeId).filter(Boolean);

    let tradeMap = {};
    let outcomeMap = {};

    if (armedTradeIds.length) {
        const [trades, outcomesById] = await Promise.all([
            api.listArmedTrades({ includeHistorical: true }).catch(() => []),
            api.getOutcomesByTradeIds(armedTradeIds).catch(() => ({}))
        ]);
        tradeMap = Object.fromEntries((trades ?? []).map((t) => [t.id, t]));
        outcomeMap = outcomesById ?? {};
    }

    nodes.eventsList.innerHTML = eventsListMarkup(page, { tradeMap, outcomeMap });
    wireOpenButtons(nodes.eventsList, "[data-open-event]", onOpenEvent);
}
