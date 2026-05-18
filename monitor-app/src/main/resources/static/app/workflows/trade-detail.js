import { api } from "../../api.js";
import {
    emptyState,
    escapeHtml,
    formatBadge,
    formatDecimal,
    formatDurationMs,
    formatFundingCountdown,
    formatInstant,
    formatNumber,
    formatSignedMs,
    journalMarkup,
    metaRow,
    modeLabel,
    openModal,
    pipelineStageMarkup,
    section,
    sideLabel,
    toLocalInputValue
} from "../shared.js";
import { t } from "../../i18n.js";
import { buildDeleteCandidateSection } from "./pipeline.js";

const CANCELLABLE_STATES = new Set(["ARMED", "ENTRY_PENDING", "ENTRY_ATTEMPTED"]);
const CLOSEABLE_STATES = new Set(["OPEN", "EXIT_PENDING"]);

function warmupSection(trade) {
    if (trade.warmupDoneAt == null && trade.warmupFallbackUsed == null) return "";
    const p50 = trade.warmupP50Ms != null ? `${trade.warmupP50Ms} ms` : "—";
    const p95 = trade.warmupP95Ms != null ? `${trade.warmupP95Ms} ms` : "—";
    const fallback = trade.warmupFallbackUsed ? t("warmup_fallback_yes") : t("warmup_fallback_no");
    return section(t("warmup_section_title"), `
        <div class="meta-grid">
            ${metaRow(t("warmup_p50"), p50)}
            ${metaRow(t("warmup_p95"), p95)}
            ${metaRow(t("warmup_fallback"), fallback)}
            ${metaRow(t("warmup_done_at"), formatInstant(trade.warmupDoneAt))}
        </div>
        <p class="meta-helper">${t("warmup_note")}</p>
    `);
}

function buildLiquiditySection(liquidity, trade) {
    const effectiveSymbol = trade.venueSymbol ?? trade.symbol;
    const assessBtn = (trade.venue && effectiveSymbol)
        ? `<button class="button" type="button" data-assess-liquidity="${trade.id}" data-venue="${escapeHtml(trade.venue)}" data-symbol="${escapeHtml(effectiveSymbol)}">${t("liquidity_assess_button")}</button>`
        : "";
    if (!liquidity) {
        return section(t("liquidity_section_title"), `
            ${emptyState(t("liquidity_no_assessment"), t("liquidity_no_assessment_detail"))}
            ${assessBtn}
        `);
    }
    const warning = liquidity.score === "UNTRADABLE"
        ? `<div class="banner">${t("liquidity_warning_untradable")}</div>`
        : liquidity.score === "THIN"
            ? `<div class="banner" style="border-color:rgba(255,190,60,0.22);background:linear-gradient(180deg,rgba(58,44,10,0.96),rgba(36,27,6,0.94))">${t("liquidity_warning_thin")}</div>`
            : "";
    const refreshBtn = (trade.venue && effectiveSymbol)
        ? `<button class="button" type="button" data-refresh-liquidity="${trade.id}" data-venue="${escapeHtml(trade.venue)}" data-symbol="${escapeHtml(effectiveSymbol)}">${t("liquidity_refresh_button")}</button>`
        : "";
    return section(t("liquidity_section_title"), `
        ${warning}
        <div class="meta-grid">
            ${metaRow(t("liquidity_score"), formatBadge("liquidity", liquidity.score))}
            ${metaRow(t("liquidity_recommended_max"), liquidity.recommendedMaxOrderNotional != null ? `${formatDecimal(liquidity.recommendedMaxOrderNotional, 2)} USD` : "—")}
            ${metaRow(t("liquidity_entry_bid_depth"), liquidity.entryBidDepthNotional != null ? `${formatDecimal(liquidity.entryBidDepthNotional, 2)} USD` : "—")}
            ${metaRow(t("liquidity_exit_ask_depth"), liquidity.exitAskDepthNotional != null ? `${formatDecimal(liquidity.exitAskDepthNotional, 2)} USD` : "—")}
            ${metaRow(t("liquidity_round_trip_safe"), liquidity.roundTripSafeNotional != null ? `${formatDecimal(liquidity.roundTripSafeNotional, 2)} USD` : "—")}
            ${metaRow(t("liquidity_spread_bps"), liquidity.spreadBps != null ? `${formatDecimal(liquidity.spreadBps, 2)} bps` : "—")}
            ${metaRow(t("liquidity_best_bid"), liquidity.bestBid != null ? formatDecimal(liquidity.bestBid, 4) : "—")}
            ${metaRow(t("liquidity_best_ask"), liquidity.bestAsk != null ? formatDecimal(liquidity.bestAsk, 4) : "—")}
            ${metaRow(t("liquidity_sampled_at"), formatInstant(liquidity.sampledAt))}
            ${metaRow(t("liquidity_expires_at"), formatInstant(liquidity.expiresAt))}
        </div>
        ${refreshBtn}
    `);
}

function buildCancelSection(trade) {
    if (CLOSEABLE_STATES.has(trade.state)) {
        return section(t("trade_close_position_title"), `
            <p class="muted">${t("trade_close_position_detail")}</p>
            <button class="button danger" type="button" data-close-trade="${trade.id}">${t("trade_close_button")}</button>
        `);
    }
    if (!CANCELLABLE_STATES.has(trade.state)) {
        return "";
    }
    return section(t("trade_cancel_title"), `
        <p class="muted">${t("trade_cancel_detail")}</p>
        <button class="button danger" type="button" data-cancel-trade="${trade.id}">${t("trade_cancel_button")}</button>
    `);
}

export function buildTradeDrawerContent({ trade, journal, attempts, liquidity }) {
    return `
        ${pipelineStageMarkup("trade")}
        ${buildLiquiditySection(liquidity, trade)}
        ${section(t("trade_parameters"), `
            <div class="meta-grid">
                ${metaRow(t("trade_status"), formatBadge("trade", trade.state))}
                ${metaRow(t("trade_funding_event"), `#${trade.fundingEventId}`)}
                ${metaRow(t("trade_source_signal"), trade.signalCandidateId ? `#${trade.signalCandidateId}` : t("label_manual"))}
                ${metaRow(t("trade_venue"), escapeHtml(trade.venue ?? "—"))}
                ${metaRow(t("trade_mode"), trade.mode ? formatBadge("venue", modeLabel(trade.mode)) : "—")}
                ${metaRow(t("trade_instrument"), escapeHtml(trade.symbol ?? "—"))}
                ${metaRow(t("trade_funding_time"), formatInstant(trade.fundingTime), formatFundingCountdown(trade.fundingTime))}
                ${metaRow(t("trade_notional"), `${formatDecimal(trade.notionalUsd, 2)} USD`)}
                ${metaRow(t("trade_side"), escapeHtml(sideLabel(trade.intendedSide)))}
                ${metaRow(t("trade_planned_entry"), formatInstant(trade.plannedEntryAt))}
                ${metaRow(t("trade_planned_exit"), formatInstant(trade.plannedExitAt))}
                ${metaRow(t("trade_entry_attempts"), formatNumber(trade.entryAttemptCount ?? 1), `${t("trade_spacing")} ${formatDurationMs(trade.entrySpacingMs ?? 0)}`)}
                ${trade.stopLossUsd != null ? metaRow(t("trade_stop_loss"), `${formatDecimal(trade.stopLossUsd, 2)} USD`) : ""}
                ${trade.takeProfitUsd != null ? metaRow(t("trade_take_profit"), `${formatDecimal(trade.takeProfitUsd, 2)} USD`) : ""}
                ${metaRow(t("trade_note"), escapeHtml(trade.notes ?? "—"))}
            </div>
            <details class="technical-details">
                <summary>${t("trade_latency_details")}</summary>
                <div class="meta-grid">
                    ${metaRow(t("trade_measured_latency"), formatDurationMs(trade.measuredEntryLatencyMs))}
                    ${metaRow(t("trade_manual_latency_adj"), formatSignedMs(trade.manualLatencyAdjustmentMs ?? 0))}
                    ${metaRow(t("trade_effective_trigger"), formatDurationMs(trade.effectiveEntryLatencyMs ?? 0))}
                    ${metaRow(t("trade_armed_at"), formatInstant(trade.armedAt))}
                    ${metaRow(t("trade_entry_lead"), formatDurationMs(trade.entryLeadMs))}
                    ${metaRow(t("trade_exit_lead"), formatDurationMs(trade.exitLeadMs))}
                    ${metaRow(t("trade_arm_source"), escapeHtml(trade.armSource ?? "—"))}
                </div>
            </details>
        `)}
        ${section(t("trade_exec_attempts"), attempts.length ? attempts.map((attempt) => `
            <div class="meta-row">
                <span class="meta-label">#${escapeHtml(attempt.attemptNumber ?? "—")} · ${escapeHtml(attempt.status)}</span>
                <strong class="meta-value">${escapeHtml(attempt.symbol)} ${escapeHtml(attempt.side)}</strong>
                <span class="meta-helper">${escapeHtml(attempt.failureReason ?? t("trade_no_error"))} · ${t("trade_trigger")} ${formatInstant(attempt.triggerAt)} · ${t("trade_recorded")} ${formatInstant(attempt.createdAt)}</span>
            </div>
        `).join("") : emptyState(t("empty_attempts"), t("empty_attempts_detail")))}
        ${warmupSection(trade)}
        ${buildCancelSection(trade)}
        ${trade.state === "ARMED" ? section(t("trade_edit_title"), `
            <details class="technical-details">
                <summary>${t("trade_edit_summary")}</summary>
                <form class="drawer-form" data-action="edit-trade" data-id="${trade.id}">
                    <fieldset class="form-group">
                        <legend>${t("event_entry_window")}</legend>
                        <div class="drawer-form-row labeled-row">
                            <label class="field">
                                <span>${t("trade_notional_usd")}</span>
                                <input name="notionalUsd" type="number" step="0.01" value="${escapeHtml(String(trade.notionalUsd ?? ""))}">
                            </label>
                            <label class="field">
                                <span>${t("trade_entry_attempts_label")}</span>
                                <input name="entryAttemptCount" type="number" min="1" max="25" step="1" value="${escapeHtml(String(trade.entryAttemptCount ?? 1))}">
                            </label>
                        </div>
                        <div class="drawer-form-row labeled-row">
                            <label class="field">
                                <span>${t("trade_planned_entry")}</span>
                                <input name="plannedEntryAt" type="datetime-local" value="${escapeHtml(toLocalInputValue(trade.plannedEntryAt) ?? "")}">
                            </label>
                            <label class="field">
                                <span>${t("trade_spacing_ms")}</span>
                                <input name="entrySpacingMs" type="number" min="0" step="1" value="${escapeHtml(String(trade.entrySpacingMs ?? 0))}">
                            </label>
                        </div>
                        <label class="field">
                            <span>${t("event_manual_latency")}</span>
                            <input name="manualLatencyAdjustmentMs" type="number" min="-60000" max="60000" step="1" value="${escapeHtml(String(trade.manualLatencyAdjustmentMs ?? 0))}">
                        </label>
                    </fieldset>
                    <fieldset class="form-group">
                        <legend>${t("event_exit_window")}</legend>
                        <label class="field">
                            <span>${t("trade_planned_exit")}</span>
                            <input name="plannedExitAt" type="datetime-local" value="${escapeHtml(toLocalInputValue(trade.plannedExitAt) ?? "")}">
                        </label>
                    </fieldset>
                    <fieldset class="form-group">
                        <legend>${t("event_risk_management")}</legend>
                        <div class="drawer-form-row labeled-row">
                            <label class="field">
                                <span>${t("event_stop_loss")}</span>
                                <input name="stopLossUsd" type="number" step="0.01" min="0" placeholder="${t("event_stop_loss_placeholder")}" value="${escapeHtml(trade.stopLossUsd != null ? String(trade.stopLossUsd) : "")}">
                            </label>
                            <label class="field">
                                <span>${t("event_take_profit")}</span>
                                <input name="takeProfitUsd" type="number" step="0.01" min="0" placeholder="${t("event_take_profit_placeholder")}" value="${escapeHtml(trade.takeProfitUsd != null ? String(trade.takeProfitUsd) : "")}">
                            </label>
                        </div>
                    </fieldset>
                    <label class="field">
                        <span>${t("trade_note")}</span>
                        <textarea name="notes">${escapeHtml(trade.notes ?? "")}</textarea>
                    </label>
                    <div class="actions">
                        <button class="button" type="submit">${t("trade_save_button")}</button>
                    </div>
                </form>
            </details>
        `) : ""}
        ${trade.signalCandidateId ? buildDeleteCandidateSection({ id: trade.signalCandidateId }, t("event_delete_source")) : ""}
        ${section("Journal", journalMarkup(journal))}
    `;
}

export async function openTradeDetail({ id, nodes, showError, onRefresh }) {
    try {
        const [trade, journal, attempts, liquidity] = await Promise.all([
            api.getArmedTrade(id),
            api.listArmedTradeJournal(id),
            api.listOrderAttempts(id),
            api.getTradeLiquidity(id)
        ]);

        nodes.modalType.textContent = t("trade_modal_type");
        nodes.modalTitle.textContent = trade.symbol ? `${trade.symbol} · ${trade.venue}` : `${t("card_trade_prefix")}${trade.id}`;
        nodes.modalContent.innerHTML = buildTradeDrawerContent({ trade, journal, attempts, liquidity });
        openModal(nodes);

        const assessLiqBtn = nodes.modalContent.querySelector("[data-assess-liquidity]");
        if (assessLiqBtn) {
            assessLiqBtn.addEventListener("click", async () => {
                const venue = assessLiqBtn.dataset.venue;
                const symbol = assessLiqBtn.dataset.symbol;
                assessLiqBtn.disabled = true;
                assessLiqBtn.textContent = t("liquidity_refreshing");
                try {
                    await api.assessLiquidity(venue, symbol, id);
                    await openTradeDetail({ id, nodes, showError, onRefresh });
                } catch (err) {
                    showError(err.message);
                    assessLiqBtn.disabled = false;
                    assessLiqBtn.textContent = t("liquidity_assess_button");
                }
            });
        }

        const refreshLiqBtn = nodes.modalContent.querySelector("[data-refresh-liquidity]");
        if (refreshLiqBtn) {
            refreshLiqBtn.addEventListener("click", async () => {
                const venue = refreshLiqBtn.dataset.venue;
                const symbol = refreshLiqBtn.dataset.symbol;
                refreshLiqBtn.disabled = true;
                refreshLiqBtn.textContent = t("liquidity_refreshing");
                try {
                    await api.refreshTradeLiquidity(id, venue, symbol);
                    await openTradeDetail({ id, nodes, showError, onRefresh });
                } catch (err) {
                    showError(err.message);
                    refreshLiqBtn.disabled = false;
                    refreshLiqBtn.textContent = t("liquidity_refresh_button");
                }
            });
        }

        const cancelBtn = nodes.modalContent.querySelector("[data-cancel-trade]");
        if (cancelBtn) {
            cancelBtn.addEventListener("click", async () => {
                cancelBtn.disabled = true;
                cancelBtn.textContent = t("trade_cancelling");
                try {
                    await api.cancelArmedTrade(trade.id);
                    if (onRefresh) onRefresh();
                    await openTradeDetail({ id, nodes, showError, onRefresh });
                } catch (err) {
                    showError(err.message);
                    cancelBtn.disabled = false;
                    cancelBtn.textContent = t("trade_cancel_button");
                }
            });
        }

        const closeBtn = nodes.modalContent.querySelector("[data-close-trade]");
        if (closeBtn) {
            closeBtn.addEventListener("click", async () => {
                closeBtn.disabled = true;
                closeBtn.textContent = t("trade_closing");
                try {
                    await api.closeArmedTrade(trade.id);
                    if (onRefresh) onRefresh();
                    await openTradeDetail({ id, nodes, showError, onRefresh });
                } catch (err) {
                    showError(err.message);
                    closeBtn.disabled = false;
                    closeBtn.textContent = t("trade_close_button");
                }
            });
        }

        const editForm = nodes.modalContent.querySelector("[data-action='edit-trade']");
        if (editForm) {
            editForm.addEventListener("submit", async (e) => {
                e.preventDefault();
                const data = new FormData(editForm);
                const submitBtn = editForm.querySelector("[type='submit']");
                submitBtn.disabled = true;
                submitBtn.textContent = t("trade_saving");
                try {
                    const toInstant = (v) => v ? new Date(v).toISOString() : null;
                    const toNum = (v) => v !== "" && v != null ? Number(v) : null;
                    await api.updateArmedTrade(trade.id, {
                        notionalUsd: toNum(data.get("notionalUsd")),
                        plannedEntryAt: toInstant(data.get("plannedEntryAt")),
                        plannedExitAt: toInstant(data.get("plannedExitAt")),
                        entryAttemptCount: toNum(data.get("entryAttemptCount")),
                        entrySpacingMs: toNum(data.get("entrySpacingMs")),
                        manualLatencyAdjustmentMs: toNum(data.get("manualLatencyAdjustmentMs")),
                        stopLossUsd: toNum(data.get("stopLossUsd")),
                        takeProfitUsd: toNum(data.get("takeProfitUsd")),
                        notes: data.get("notes") || null
                    });
                    if (onRefresh) onRefresh();
                    await openTradeDetail({ id, nodes, showError, onRefresh });
                } catch (err) {
                    showError(err.message);
                    submitBtn.disabled = false;
                    submitBtn.textContent = t("trade_save_button");
                }
            });
        }
    } catch (error) {
        showError(error.message);
    }
}
