import { api } from "../../api.js";
import { emptyState, tradeCard } from "../shared.js";
import { buildTradeExpansionContent } from "../workflows/trade-detail.js";
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

export async function renderTrades({ nodes, trades, showError, onRefresh }) {
    const outcomes = await loadOutcomes(trades);
    nodes.tradesList.innerHTML = tradesListMarkup(trades, outcomes);
    wireTradeCardExpansion(nodes.tradesList, { showError, onRefresh });
}

function wireTradeCardExpansion(container, { showError, onRefresh }) {
    container.addEventListener("toggle", async (e) => {
        const details = e.target.closest("[data-lazy-trade]");
        if (!details || !details.open) return;

        const tradeId = details.dataset.lazyTrade;
        const contentEl = details.querySelector(".card-full-content");
        if (!contentEl || !contentEl.querySelector(".card-loading")) return;

        try {
            const [trade, attempts, liquidity, position, outcome] = await Promise.all([
                api.getArmedTrade(tradeId),
                api.listOrderAttempts(tradeId).catch(() => []),
                api.getTradeLiquidity(tradeId).catch(() => null),
                api.getTradePosition(tradeId).catch(() => null),
                api.getTradeOutcome(tradeId).catch(() => null)
            ]);

            contentEl.innerHTML = buildTradeExpansionContent({ trade, attempts, liquidity, position, outcome });

            wireEditForm(contentEl, { showError, onRefresh });
            wireCancelClose(contentEl, { showError, onRefresh });
            wireAssessLiquidity(contentEl, { showError, onRefresh });
        } catch (err) {
            contentEl.innerHTML = `<p class="card-loading">${err.message}</p>`;
        }
    }, true);
}

function wireEditForm(container, { showError, onRefresh }) {
    const form = container.querySelector("[data-action='edit-trade']");
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
            await api.updateArmedTrade(id, {
                notionalUsd: Number(data.get("notionalUsd")),
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

function wireCancelClose(container, { showError, onRefresh }) {
    container.addEventListener("click", async (e) => {
        const cancelBtn = e.target.closest("[data-cancel-trade]");
        if (cancelBtn) {
            cancelBtn.disabled = true;
            try {
                await api.cancelArmedTrade(cancelBtn.dataset.cancelTrade);
                if (onRefresh) await onRefresh();
            } catch (err) {
                cancelBtn.disabled = false;
                showError(err.message);
            }
            return;
        }
        const closeBtn = e.target.closest("[data-close-trade]");
        if (closeBtn) {
            closeBtn.disabled = true;
            try {
                await api.closeArmedTrade(closeBtn.dataset.closeTrade);
                if (onRefresh) await onRefresh();
            } catch (err) {
                closeBtn.disabled = false;
                showError(err.message);
            }
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
