import { api } from "../../api.js";
import { emptyState, eventCard, escapeHtml, formatBadge, formatDecimal, formatFundingCountdown, formatInstant, venueIcon } from "../shared.js";
import { buildEventExpansionContent } from "../workflows/event-detail.js";
import { t } from "../../i18n.js";
import { renderLayerBlock } from "../components/layer-block.js";
import { renderEnrichmentTimestamp } from "../components/enrichment-timestamp.js";

function _eventBaseLayerStatus(event) {
    if (event.status === "ARMED") return "ok";
    if (event.status === "DISCOVERED") return "warn";
    return "blocked";
}

function _eventLiquidityLayerStatus(tradeLiquidity) {
    if (!tradeLiquidity) return "missing";
    const s = tradeLiquidity.score;
    if (s === "EXCELLENT" || s === "GOOD" || s === "MEDIUM") return "ok";
    if (s === "THIN") return "warn";
    return "blocked";
}

function buildEventCardLayerBlocks(event, { trade = null } = {}) {
    const ratePct = event.fundingRatePct != null ? Number(event.fundingRatePct) : null;
    const rateTone = ratePct == null ? "neutral" : ratePct < 0 ? "bad" : ratePct > 0.005 ? "good" : "neutral";
    const rateChip = ratePct != null
        ? `<span class="chip chip-${rateTone}" title="${t("card_rate")}">${ratePct >= 0 ? "+" : ""}${formatDecimal(ratePct, 6)}%</span>`
        : "";
    const countdown = formatFundingCountdown(event.fundingTime);

    const baseContent = `
        <div class="chip-row" style="margin-bottom:4px">
            ${rateChip}
            <span class="chip chip-muted" title="${t("event_funding_time")}">${formatInstant(event.fundingTime)}</span>
            <span class="chip chip-muted">${countdown}</span>
        </div>
        ${renderEnrichmentTimestamp(event.discoveredAt, "DISCOVERY")}
    `;

    const baseBlock = renderLayerBlock({
        layerType: "base",
        layerName: t("layer.base") || "Base",
        decoratorName: "FUNDING_API",
        timestamp: event.discoveredAt,
        source: "DISCOVERY",
        status: _eventBaseLayerStatus(event),
        collapsed: false,
        content: baseContent
    });

    return baseBlock;
}

function eventCardLayered(event, { trade = null, outcome = null } = {}) {
    const expandLabel = event.status === "DISCOVERED" ? t("card_expand_arm") : t("card_expand_details");
    const baseLayerBlock = buildEventCardLayerBlocks(event, { trade });

    let tradeChips = "";
    if (trade) {
        const effLat = trade.effectiveEntryLatencyMs ?? trade.measuredEntryLatencyMs;
        tradeChips = `
            ${formatBadge("trade", trade.state)}
            <span class="chip chip-muted" title="${t("trade_notional")}">${formatDecimal(trade.notionalUsd, 2)} USD</span>
            ${effLat != null ? `<span class="chip chip-muted" title="${t("trade_effective_trigger")}">${effLat}ms</span>` : ""}
        `;
    }

    const net = outcome?.netPnlUsd != null ? Number(outcome.netPnlUsd) : null;
    const pnlChip = net != null
        ? `<span class="chip chip-${net >= 0 ? "good" : "bad"}" title="Net PnL">${net >= 0 ? "+" : ""}${formatDecimal(net, 4)} USD</span>`
        : "";

    return `
        <article class="list-item event-card" data-event-id="${event.id}">
            <header>
                <div>
                    <h3 class="item-title">${venueIcon(event.venue)}${escapeHtml(event.symbol)}</h3>
                    <p class="muted">${escapeHtml(event.venue)}</p>
                </div>
                <div class="actions">
                    ${formatBadge("event", event.status)}
                </div>
            </header>
            ${baseLayerBlock}
            ${tradeChips || pnlChip ? `<div class="chip-row">${tradeChips}${pnlChip}</div>` : ""}
            <details class="card-expansion" data-lazy-event="${event.id}">
                <summary class="card-expand-toggle">${expandLabel}</summary>
                <div class="card-full-content">
                    <p class="card-loading">…</p>
                </div>
            </details>
        </article>
    `;
}

export function eventsListMarkup(page = {}, { tradeMap = {}, outcomeMap = {} } = {}) {
    const events = page?.content ?? [];
    return events.length
        ? events.map((e) => eventCardLayered(e, {
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
    if (!nodes.eventsList._expansionWired) {
        nodes.eventsList._expansionWired = true;
        wireEventCardExpansion(nodes.eventsList, { showError, onRefresh });
    }
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
            wireDeleteSource(contentEl, { showError, onRefresh });
        } catch (err) {
            contentEl.innerHTML = `<p class="card-loading">${escapeHtml(err.message)}</p>`;
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
        const assessBtn = e.target.closest("[data-assess-liquidity]");
        if (assessBtn) {
            const venue = assessBtn.dataset.venue;
            const symbol = assessBtn.dataset.symbol;
            const tradeId = assessBtn.dataset.assessLiquidity;
            assessBtn.disabled = true;
            try {
                await api.assessLiquidity(venue, symbol, tradeId);
                if (onRefresh) await onRefresh();
            } catch (err) {
                assessBtn.disabled = false;
                showError(err.message);
            }
            return;
        }
        const refreshBtn = e.target.closest("[data-refresh-liquidity]");
        if (refreshBtn) {
            const tradeId = refreshBtn.dataset.refreshLiquidity;
            const venue = refreshBtn.dataset.venue;
            const symbol = refreshBtn.dataset.symbol;
            refreshBtn.disabled = true;
            try {
                await api.refreshTradeLiquidity(tradeId, venue, symbol);
                if (onRefresh) await onRefresh();
            } catch (err) {
                refreshBtn.disabled = false;
                showError(err.message);
            }
        }
    });
}

function wireDeleteSource(container, { showError, onRefresh }) {
    const form = container.querySelector("[data-action='delete-candidate']");
    if (!form) return;
    form.addEventListener("submit", async (e) => {
        e.preventDefault();
        const note = new FormData(form).get("deleteNote") ?? "";
        const btn = form.querySelector("[type='submit']");
        btn.disabled = true;
        try {
            await api.deleteCandidate(form.dataset.id, note);
            if (onRefresh) await onRefresh();
        } catch (err) {
            btn.disabled = false;
            showError(err.message);
        }
    });
}
