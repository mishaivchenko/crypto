import { api } from "../../api.js";
import { emptyState, eventCard } from "../shared.js";
import { buildEventExpansionContent } from "../workflows/event-detail.js";
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

export async function renderFundingEvents({ nodes, page, showError, onRefresh }) {
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
    wireEventCardExpansion(nodes.eventsList, { showError, onRefresh });
}

function wireEventCardExpansion(container, { showError, onRefresh }) {
    container.addEventListener("toggle", async (e) => {
        const details = e.target.closest("[data-lazy-event]");
        if (!details || !details.open) return;

        const eventId = details.dataset.lazyEvent;
        const contentEl = details.querySelector(".card-full-content");
        if (!contentEl || !contentEl.querySelector(".card-loading")) return;

        try {
            const event = await api.getFundingEvent(eventId);

            let candidate = null, liquidity = null;
            if (event.signalCandidateId) {
                [candidate, liquidity] = await Promise.all([
                    api.getCandidate(event.signalCandidateId).catch(() => null),
                    api.getCandidateLiquidity(event.signalCandidateId).catch(() => null)
                ]);
            }

            let trade = null, attempts = [], tradeLiquidity = null, outcome = null, position = null;
            if (event.armedTradeId) {
                [trade, attempts, tradeLiquidity, outcome, position] = await Promise.all([
                    api.getArmedTrade(event.armedTradeId).catch(() => null),
                    api.listOrderAttempts(event.armedTradeId).catch(() => []),
                    api.getTradeLiquidity(event.armedTradeId).catch(() => null),
                    api.getTradeOutcome(event.armedTradeId).catch(() => null),
                    api.getTradePosition(event.armedTradeId).catch(() => null)
                ]);
            }

            contentEl.innerHTML = buildEventExpansionContent({ event, candidate, liquidity, trade, attempts, tradeLiquidity, outcome, position });

            wireArmForm(contentEl, { showError, onRefresh });
            wireAssessLiquidity(contentEl, { showError, onRefresh });
        } catch (err) {
            contentEl.innerHTML = `<p class="card-loading">${err.message}</p>`;
        }
    }, true);
}

function wireArmForm(container, { showError, onRefresh }) {
    const form = container.querySelector("[data-action='arm-event']");
    if (!form) return;
    form.addEventListener("submit", async (e) => {
        e.preventDefault();
        const data = new FormData(form);
        const id = form.dataset.id;
        const btn = form.querySelector("[type='submit']");
        btn.disabled = true;
        const orig = btn.textContent;
        btn.textContent = "…";
        try {
            await api.armFundingEvent(id, {
                notionalUsd: Number(data.get("notionalUsd")),
                intendedSide: data.get("intendedSide"),
                plannedEntryAt: data.get("plannedEntryAt") ? new Date(data.get("plannedEntryAt")).toISOString() : null,
                plannedExitAt: data.get("plannedExitAt") ? new Date(data.get("plannedExitAt")).toISOString() : null,
                entryAttemptCount: Number(data.get("entryAttemptCount")),
                entrySpacingMs: Number(data.get("entrySpacingMs")),
                manualLatencyAdjustmentMs: Number(data.get("manualLatencyAdjustmentMs")),
                stopLossUsd: data.get("stopLossUsd") ? Number(data.get("stopLossUsd")) : null,
                takeProfitUsd: data.get("takeProfitUsd") ? Number(data.get("takeProfitUsd")) : null,
                notes: data.get("notes") || null
            });
            if (onRefresh) await onRefresh();
        } catch (err) {
            btn.disabled = false;
            btn.textContent = orig;
            showError(err.message);
        }
    });
}

function wireAssessLiquidity(container, { showError, onRefresh }) {
    container.addEventListener("click", async (e) => {
        const btn = e.target.closest("[data-assess-liquidity],[data-refresh-liquidity]");
        if (!btn) return;
        const id = btn.dataset.assessLiquidity || btn.dataset.refreshLiquidity;
        const venue = btn.dataset.venue;
        const symbol = btn.dataset.symbol;
        btn.disabled = true;
        try {
            await api.assessTradeLiquidity(id, { venue, symbol });
            if (onRefresh) await onRefresh();
        } catch (err) {
            btn.disabled = false;
            showError(err.message);
        }
    });
}
