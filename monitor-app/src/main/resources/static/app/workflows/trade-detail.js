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
    toLocalInputValue,
    venueIcon
} from "../shared.js";
import { t } from "../../i18n.js";
import { buildDeleteCandidateSection } from "./pipeline.js";

function infoTip(text) {
    return `<details class="info-tip"><summary class="info-tip-trigger">ⓘ</summary><div class="info-tip-body">${escapeHtml(text)}</div></details>`;
}

function kvItem(label, value) {
    return `<div class="kv-item"><span class="kv-label">${label}</span><span class="kv-value">${value}</span></div>`;
}

const CANCELLABLE_STATES = new Set(["ARMED", "ENTRY_PENDING", "ENTRY_ATTEMPTED"]);
const CLOSEABLE_STATES = new Set(["OPEN", "EXIT_PENDING"]);

function buildLiquiditySection(liquidity, trade) {
    const effectiveSymbol = trade.venueSymbol ?? trade.symbol;
    const assessBtn = (trade.venue && effectiveSymbol)
        ? `<button class="chip chip-btn" type="button" data-assess-liquidity="${trade.id}" data-venue="${escapeHtml(trade.venue)}" data-symbol="${escapeHtml(effectiveSymbol)}">${t("liquidity_assess_button")}</button>`
        : "";
    const refreshBtn = (trade.venue && effectiveSymbol)
        ? `<button class="chip chip-btn" type="button" data-refresh-liquidity="${trade.id}" data-venue="${escapeHtml(trade.venue)}" data-symbol="${escapeHtml(effectiveSymbol)}">${t("liquidity_refresh_button")}</button>`
        : "";

    if (!liquidity) {
        return section(t("liquidity_section_title"), `
            <p class="muted">${t("liquidity_no_assessment")}</p>
            <div class="chip-row" style="margin-top:8px">${assessBtn}</div>
        `);
    }
    const scoreTone = liquidity.score === "EXCELLENT" || liquidity.score === "GOOD" ? "good"
        : liquidity.score === "THIN" || liquidity.score === "UNTRADABLE" ? "bad" : "warning";
    const warning = liquidity.score === "UNTRADABLE"
        ? `<div class="banner">${t("liquidity_warning_untradable")}</div>`
        : liquidity.score === "THIN"
            ? `<div class="banner" style="border-color:rgba(255,190,60,0.22);background:linear-gradient(180deg,rgba(58,44,10,0.96),rgba(36,27,6,0.94))">${t("liquidity_warning_thin")}</div>`
            : "";
    return section(t("liquidity_section_title"), `
        ${warning}
        <p class="liquidity-summary">
            <span class="chip chip-${scoreTone}">${escapeHtml(t(`liquidity_score_${liquidity.score}`) ?? liquidity.score)}</span>
            ${liquidity.bestBid != null ? `<span>${formatDecimal(liquidity.bestBid, 4)} / ${liquidity.bestAsk != null ? formatDecimal(liquidity.bestAsk, 4) : "—"}</span>` : ""}
            ${liquidity.spreadBps != null ? `<span class="chip ${liquidity.spreadBps > 20 ? "chip-bad" : "chip-muted"}">${formatDecimal(liquidity.spreadBps, 1)} bps</span>` : ""}
            ${liquidity.recommendedMaxOrderNotional != null ? `<span class="chip chip-muted">&le;${formatDecimal(liquidity.recommendedMaxOrderNotional, 0)} USD</span>` : ""}
            ${refreshBtn}
        </p>
        <div class="kv-row">
            ${kvItem(`${t("liquidity_entry_bid_depth")} ${infoTip(t("tip_bid_depth"))}`, liquidity.entryBidDepthNotional != null ? `${formatDecimal(liquidity.entryBidDepthNotional, 2)} USD` : "—")}
            ${kvItem(`${t("liquidity_exit_ask_depth")} ${infoTip(t("tip_ask_depth"))}`, liquidity.exitAskDepthNotional != null ? `${formatDecimal(liquidity.exitAskDepthNotional, 2)} USD` : "—")}
            ${kvItem(`${t("liquidity_round_trip_safe")} ${infoTip(t("tip_round_trip"))}`, liquidity.roundTripSafeNotional != null ? `${formatDecimal(liquidity.roundTripSafeNotional, 2)} USD` : "—")}
            ${kvItem(t("liquidity_sampled_at"), formatInstant(liquidity.sampledAt))}
        </div>
    `);
}

export function buildLatencyChainSection(trade) {
    const p50 = trade.warmupP50Ms ?? trade.measuredEntryLatencyMs;
    const manualAdj = trade.manualLatencyAdjustmentMs ?? 0;
    const effectiveLead = trade.effectiveEntryLatencyMs ?? 0;
    const p50Label = p50 != null ? `${p50}ms` : "—";
    const adjLabel = formatSignedMs(manualAdj);

    return section(t("event_latency_chain"), `
        <div class="latency-summary">
            <span class="formula-item">${p50Label} p50 ${infoTip(t("tip_latency_p50"))}</span>
            <span class="formula-op">+</span>
            <span class="formula-item">${adjLabel} adj ${infoTip(t("tip_latency_adj"))}</span>
            <span class="formula-op">=</span>
            <strong class="formula-item formula-result">${effectiveLead}ms ${t("trade_effective_trigger")} ${infoTip(t("tip_latency_effective"))}</strong>
        </div>
        <div class="kv-row">
            ${kvItem(t("trade_measured_latency"), formatDurationMs(trade.measuredEntryLatencyMs))}
            ${kvItem(t("trade_armed_at"), formatInstant(trade.armedAt))}
            ${kvItem(t("trade_entry_lead"), formatDurationMs(trade.entryLeadMs))}
            ${kvItem(t("trade_exit_lead"), formatDurationMs(trade.exitLeadMs))}
            ${trade.armSource ? kvItem(t("trade_arm_source"), escapeHtml(trade.armSource)) : ""}
        </div>
    `);
}

function warmupSection(trade) {
    if (!trade.warmupFallbackUsed) return "";
    return section(t("warmup_section_title"), `
        <p class="warmup-warning">${t("warmup_fallback_warning")}</p>
    `);
}

export function buildPositionSection(position) {
    if (!position) return "";
    return section(t("event_position"), `
        <div class="meta-grid">
            ${metaRow(t("event_entry_price"), position.entryPrice != null ? formatDecimal(position.entryPrice, 4) : "—")}
            ${metaRow(t("event_exit_price"), position.exitPrice != null ? formatDecimal(position.exitPrice, 4) : "—")}
            ${metaRow(t("history_quantity"), position.quantity != null ? formatDecimal(position.quantity, 6) : "—")}
            ${metaRow(t("history_opened_at"), formatInstant(position.openedAt))}
            ${position.closedAt ? metaRow(t("history_closed_at"), formatInstant(position.closedAt)) : ""}
        </div>
    `);
}

export function buildOutcomeSection(outcome) {
    if (!outcome) return "";
    const net = outcome.netPnlUsd != null ? Number(outcome.netPnlUsd) : null;
    const netTone = net == null ? "muted" : net >= 0 ? "good" : "bad";
    return section(t("event_outcome"), `
        <div class="chip-row" style="margin-bottom:8px">
            ${net != null ? `<span class="chip chip-${netTone}">${net >= 0 ? "+" : ""}${formatDecimal(net, 4)} USD</span>` : ""}
            ${outcome.outcomeCode ? `<span class="chip chip-muted">${escapeHtml(outcome.outcomeCode)}</span>` : ""}
        </div>
        <div class="meta-grid">
            ${metaRow(t("event_gross_pnl"), outcome.grossPnlUsd != null ? `${formatDecimal(outcome.grossPnlUsd, 4)} USD` : "—")}
            ${metaRow(t("event_net_pnl"), net != null ? `${net >= 0 ? "+" : ""}${formatDecimal(net, 4)} USD` : "—")}
            ${metaRow(t("event_fees"), outcome.feesUsd != null ? `${formatDecimal(outcome.feesUsd, 4)} USD` : "—")}
            ${metaRow(t("history_evaluated_at"), formatInstant(outcome.evaluatedAt))}
        </div>
    `);
}

export function buildCancelSection(trade) {
    if (CLOSEABLE_STATES.has(trade.state)) {
        return `<div class="action-row">
            <button class="btn-danger-small" type="button" data-close-trade="${trade.id}">✕ ${t("trade_close_button")}</button>
        </div>`;
    }
    if (!CANCELLABLE_STATES.has(trade.state)) {
        return "";
    }
    return `<div class="action-row">
        <button class="btn-danger-small" type="button" data-cancel-trade="${trade.id}">✕ ${t("trade_cancel_button")}</button>
    </div>`;
}

export function buildTradeExpansionContent({ trade, attempts = [], liquidity = null, position = null, outcome = null }) {
    return `
        ${buildLiquiditySection(liquidity, trade)}
        ${buildLatencyChainSection(trade)}
        ${attempts.length ? section(t("trade_exec_attempts"), attempts.map((attempt) => `
            <div class="meta-row">
                <span class="meta-label">#${escapeHtml(String(attempt.attemptNumber ?? "—"))} · ${escapeHtml(attempt.status)}</span>
                <strong class="meta-value">${escapeHtml(attempt.symbol ?? "—")} ${escapeHtml(attempt.side ?? "—")}</strong>
                <span class="meta-helper">
                    ${attempt.averageFillPrice != null ? `${t("event_fill_price")} ${formatDecimal(attempt.averageFillPrice, 4)} · ` : ""}
                    ${attempt.feeUsd != null ? `${t("event_fees")} ${formatDecimal(attempt.feeUsd, 4)} USD · ` : ""}
                    ${escapeHtml(attempt.failureReason ?? t("trade_no_error"))} · ${t("trade_trigger")} ${formatInstant(attempt.triggerAt)}
                </span>
            </div>
        `).join("")) : ""}
        ${warmupSection(trade)}
        ${buildPositionSection(position)}
        ${buildOutcomeSection(outcome)}
        ${buildCancelSection(trade)}
        ${trade.state === "ARMED" ? `
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
                                <input name="plannedEntryAt" type="datetime-local" step="0.001" value="${escapeHtml(toLocalInputValue(trade.plannedEntryAt) ?? "")}">
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
                            <input name="plannedExitAt" type="datetime-local" step="0.001" value="${escapeHtml(toLocalInputValue(trade.plannedExitAt) ?? "")}">
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
        ` : ""}
        ${trade.signalCandidateId ? buildDeleteCandidateSection({ id: trade.signalCandidateId }, t("event_delete_source")) : ""}
    `;
}

export function buildTradeDrawerContent({ trade, journal, attempts, liquidity, position = null, outcome = null }) {
    return `
        ${pipelineStageMarkup("trade")}
        ${buildLiquiditySection(liquidity, trade)}
        ${section(t("trade_parameters"), `
            <div class="chip-row" style="margin-bottom:8px">
                ${formatBadge("trade", trade.state)}
                <span class="chip chip-muted">${formatDecimal(trade.notionalUsd, 2)} USD</span>
                <span class="chip chip-muted">${escapeHtml(sideLabel(trade.intendedSide))}</span>
                ${trade.mode ? `<span class="chip ${trade.mode === "testnet" ? "chip-warning" : "chip-muted"}">${escapeHtml(modeLabel(trade.mode))}</span>` : ""}
            </div>
            <div class="meta-grid">
                ${metaRow(t("trade_funding_event"), `#${trade.fundingEventId}`)}
                ${metaRow(t("trade_source_signal"), trade.signalCandidateId ? `#${trade.signalCandidateId}` : t("label_manual"))}
                ${metaRow(t("trade_venue"), escapeHtml(trade.venue ?? "—"))}
                ${metaRow(t("trade_instrument"), escapeHtml(trade.symbol ?? "—"))}
                ${metaRow(t("trade_funding_time"), formatInstant(trade.fundingTime), formatFundingCountdown(trade.fundingTime))}
                ${metaRow(t("trade_planned_entry"), formatInstant(trade.plannedEntryAt))}
                ${metaRow(t("trade_planned_exit"), formatInstant(trade.plannedExitAt))}
                ${metaRow(t("trade_entry_attempts"), formatNumber(trade.entryAttemptCount ?? 1), `${t("trade_spacing")} ${formatDurationMs(trade.entrySpacingMs ?? 0)}`)}
                ${trade.stopLossUsd != null ? metaRow(t("trade_stop_loss"), `${formatDecimal(trade.stopLossUsd, 2)} USD`) : ""}
                ${trade.takeProfitUsd != null ? metaRow(t("trade_take_profit"), `${formatDecimal(trade.takeProfitUsd, 2)} USD`) : ""}
                ${metaRow(t("trade_note"), escapeHtml(trade.notes ?? "—"))}
            </div>
        `)}
        ${buildLatencyChainSection(trade)}
        ${section(t("trade_exec_attempts"), attempts.length ? attempts.map((attempt) => `
            <div class="meta-row">
                <span class="meta-label">#${escapeHtml(String(attempt.attemptNumber ?? "—"))} · ${escapeHtml(attempt.status)}</span>
                <strong class="meta-value">${escapeHtml(attempt.symbol ?? "—")} ${escapeHtml(attempt.side ?? "—")}</strong>
                <span class="meta-helper">
                    ${attempt.averageFillPrice != null ? `${t("event_fill_price")} ${formatDecimal(attempt.averageFillPrice, 4)} · ` : ""}
                    ${attempt.feeUsd != null ? `${t("event_fees")} ${formatDecimal(attempt.feeUsd, 4)} USD · ` : ""}
                    ${escapeHtml(attempt.failureReason ?? t("trade_no_error"))} · ${t("trade_trigger")} ${formatInstant(attempt.triggerAt)} · ${t("trade_recorded")} ${formatInstant(attempt.createdAt)}
                </span>
            </div>
        `).join("") : emptyState(t("empty_attempts"), t("empty_attempts_detail")))}
        ${warmupSection(trade)}
        ${buildPositionSection(position)}
        ${buildOutcomeSection(outcome)}
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
                                <input name="plannedEntryAt" type="datetime-local" step="0.001" value="${escapeHtml(toLocalInputValue(trade.plannedEntryAt) ?? "")}">
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
                            <input name="plannedExitAt" type="datetime-local" step="0.001" value="${escapeHtml(toLocalInputValue(trade.plannedExitAt) ?? "")}">
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
        const [trade, journal, attempts, liquidity, position, outcome] = await Promise.all([
            api.getArmedTrade(id),
            api.listArmedTradeJournal(id),
            api.listOrderAttempts(id),
            api.getTradeLiquidity(id).catch(() => null),
            api.getTradePosition(id).catch(() => null),
            api.getTradeOutcome(id).catch(() => null)
        ]);

        nodes.modalType.textContent = t("trade_modal_type");
        nodes.modalTitle.innerHTML = trade.symbol
            ? `${venueIcon(trade.venue)}${escapeHtml(trade.symbol)} · ${escapeHtml(trade.venue)}`
            : escapeHtml(`${t("card_trade_prefix")}${trade.id}`);
        nodes.modalContent.innerHTML = buildTradeDrawerContent({ trade, journal, attempts, liquidity, position, outcome });
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
