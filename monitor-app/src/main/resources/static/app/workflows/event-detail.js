import { api } from "../../api.js";
import {
    escapeHtml,
    formatAiBadge,
    formatBadge,
    formatDecimal,
    formatDurationMs,
    formatFundingCountdown,
    formatInstant,
    formatSignedMs,
    journalMarkup,
    metaRow,
    offsetIso,
    openModal,
    pipelineStageMarkup,
    section,
    sourceLabel,
    toLocalInputValue,
    venueIcon
} from "../shared.js";
import { buildDeleteCandidateSection } from "./pipeline.js";
import { t } from "../../i18n.js";

function infoTip(text) {
    return `<details class="info-tip"><summary class="info-tip-trigger">ⓘ</summary><div class="info-tip-body">${escapeHtml(text)}</div></details>`;
}

function kvItem(label, value) {
    return `<div class="kv-item"><span class="kv-label">${label}</span><span class="kv-value">${value}</span></div>`;
}

function signalAnalysisChips(candidate, liquidity) {
    if (!candidate) return "";
    const ai = candidate.aiAdvice;
    const aiTone = !ai ? "muted" : ai.recommendation === "GO" ? "good" : ai.recommendation === "PASS" ? "bad" : "warning";
    const scoreTone = !liquidity ? "muted"
        : liquidity.score === "EXCELLENT" || liquidity.score === "GOOD" ? "good"
        : liquidity.score === "THIN" || liquidity.score === "UNTRADABLE" ? "bad" : "warning";

    const aiPart = ai
        ? `<span class="chip chip-${aiTone}">${escapeHtml(t(`ai_recommendation_${ai.recommendation}`) ?? ai.recommendation)}</span>
           <span class="chip chip-muted">${Math.round(ai.confidence * 100)}%</span>
           ${ai.reasoning ? `<details class="chip-details"><summary class="chip chip-muted">${escapeHtml(ai.modelUsed ?? "AI")}</summary><p class="chip-details-body">${escapeHtml(ai.reasoning)}</p></details>` : ""}`
        : `<span class="chip chip-muted">${t("ai_recommendation_pending")}</span>`;

    const liqPart = liquidity
        ? `<span class="chip chip-${scoreTone}">${escapeHtml(t(`liquidity_score_${liquidity.score}`) ?? liquidity.score)}</span>
           ${liquidity.bestBid != null ? `<span class="chip chip-muted">${formatDecimal(liquidity.bestBid, 4)} / ${formatDecimal(liquidity.bestAsk, 4)}</span>` : ""}
           ${liquidity.spreadBps != null ? `<span class="chip ${liquidity.spreadBps > 20 ? "chip-bad" : "chip-muted"}">${formatDecimal(liquidity.spreadBps, 1)} bps</span>` : ""}
           ${liquidity.recommendedMaxOrderNotional != null ? `<span class="chip chip-muted">&le;${formatDecimal(liquidity.recommendedMaxOrderNotional, 0)} USD</span>` : ""}`
        : `<span class="chip chip-muted">${t("event_liquidity_unavailable")}</span>`;

    return `
        <div class="event-signal-analysis">
            <div class="chip-row">${aiPart}</div>
            <div class="chip-row">${liqPart}</div>
        </div>`;
}

function buildTradeParamsSection(trade) {
    const extras = [
        trade.stopLossUsd != null ? `SL ${formatDecimal(trade.stopLossUsd, 2)} USD` : "",
        trade.takeProfitUsd != null ? `TP ${formatDecimal(trade.takeProfitUsd, 2)} USD` : "",
        trade.notes ? `"${escapeHtml(trade.notes)}"` : ""
    ].filter(Boolean);
    if (!extras.length) return "";
    return `<p class="trade-extras">${extras.join(" · ")}</p>`;
}

function buildLatencyChainSection(trade) {
    const p50 = trade.warmupP50Ms ?? trade.measuredEntryLatencyMs;
    const manualAdj = trade.manualLatencyAdjustmentMs ?? 0;
    const effectiveLead = trade.effectiveEntryLatencyMs ?? 0;
    const p50Label = p50 != null ? `${p50}ms` : "—";
    const adjLabel = formatSignedMs(manualAdj);

    return section(t("event_latency_chain"), `
        <p class="latency-summary">
            <span>${p50Label} p50 ${infoTip(t("tip_latency_p50"))}</span>
            <span style="color:var(--muted)">+</span>
            <span>${adjLabel} adj ${infoTip(t("tip_latency_adj"))}</span>
            <span style="color:var(--muted)">=</span>
            <strong>${formatDurationMs(effectiveLead)} ${t("trade_effective_trigger")} ${infoTip(t("tip_latency_effective"))}</strong>
        </p>
        <div class="kv-row">
            ${kvItem(t("trade_armed_at"), formatInstant(trade.armedAt))}
            ${kvItem(t("trade_measured_latency"), formatDurationMs(trade.measuredEntryLatencyMs))}
            ${kvItem(t("trade_entry_lead"), formatDurationMs(trade.entryLeadMs))}
            ${kvItem(t("trade_exit_lead"), formatDurationMs(trade.exitLeadMs))}
            ${trade.armSource ? kvItem(t("trade_arm_source"), escapeHtml(trade.armSource)) : ""}
        </div>
    `);
}

function buildTradeLiquiditySection(tradeLiquidity) {
    if (!tradeLiquidity) return "";
    const scoreTone = tradeLiquidity.score === "EXCELLENT" || tradeLiquidity.score === "GOOD" ? "good"
        : tradeLiquidity.score === "THIN" || tradeLiquidity.score === "UNTRADABLE" ? "bad" : "warning";
    const warning = tradeLiquidity.score === "UNTRADABLE"
        ? `<div class="banner">${t("liquidity_warning_untradable")}</div>`
        : tradeLiquidity.score === "THIN"
            ? `<div class="banner" style="border-color:rgba(255,190,60,0.22);background:linear-gradient(180deg,rgba(58,44,10,0.96),rgba(36,27,6,0.94))">${t("liquidity_warning_thin")}</div>`
            : "";
    return section(t("event_trade_liquidity"), `
        ${warning}
        <p class="liquidity-summary">
            <span class="chip chip-${scoreTone}">${escapeHtml(t(`liquidity_score_${tradeLiquidity.score}`) ?? tradeLiquidity.score)}</span>
            ${tradeLiquidity.bestBid != null ? `<span>${formatDecimal(tradeLiquidity.bestBid, 4)} / ${tradeLiquidity.bestAsk != null ? formatDecimal(tradeLiquidity.bestAsk, 4) : "—"}</span>` : ""}
            ${tradeLiquidity.spreadBps != null ? `<span class="chip ${tradeLiquidity.spreadBps > 20 ? "chip-bad" : "chip-muted"}">${formatDecimal(tradeLiquidity.spreadBps, 1)} bps</span>` : ""}
            ${tradeLiquidity.recommendedMaxOrderNotional != null ? `<span class="chip chip-muted">&le;${formatDecimal(tradeLiquidity.recommendedMaxOrderNotional, 0)} USD</span>` : ""}
        </p>
        <div class="kv-row">
            ${kvItem(`${t("liquidity_entry_bid_depth")} ${infoTip(t("tip_bid_depth"))}`, tradeLiquidity.entryBidDepthNotional != null ? `${formatDecimal(tradeLiquidity.entryBidDepthNotional, 2)} USD` : "—")}
            ${kvItem(`${t("liquidity_exit_ask_depth")} ${infoTip(t("tip_ask_depth"))}`, tradeLiquidity.exitAskDepthNotional != null ? `${formatDecimal(tradeLiquidity.exitAskDepthNotional, 2)} USD` : "—")}
            ${kvItem(`${t("liquidity_round_trip_safe")} ${infoTip(t("tip_round_trip"))}`, tradeLiquidity.roundTripSafeNotional != null ? `${formatDecimal(tradeLiquidity.roundTripSafeNotional, 2)} USD` : "—")}
            ${kvItem(t("liquidity_sampled_at"), formatInstant(tradeLiquidity.sampledAt))}
        </div>
    `);
}

function buildAttemptsSection(attempts) {
    if (!attempts || !attempts.length) return "";
    return section(t("event_execution_attempts"), attempts.map((a) => `
        <div class="meta-row">
            <span class="meta-label">#${escapeHtml(String(a.attemptNumber ?? "—"))} · ${escapeHtml(a.status)}</span>
            <strong class="meta-value">${escapeHtml(a.symbol ?? "—")} ${escapeHtml(a.side ?? "—")}</strong>
            <span class="meta-helper">
                ${a.averageFillPrice != null ? `${t("event_fill_price")} ${formatDecimal(a.averageFillPrice, 4)} · ` : ""}
                ${a.filledQuantity != null ? `qty ${formatDecimal(a.filledQuantity, 6)} · ` : ""}
                ${a.feeUsd != null ? `${t("event_fees")} ${formatDecimal(a.feeUsd, 4)} USD · ` : ""}
                ${a.failureReason ? escapeHtml(a.failureReason) + " · " : ""}
                ${t("trade_trigger")} ${formatInstant(a.triggerAt)}
            </span>
        </div>
    `).join(""));
}

function buildPositionSection(position) {
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

function buildOutcomeSection(outcome) {
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

export function buildArmForm(event, suggestedNotional = 25) {
    const defaultEntry = toLocalInputValue(offsetIso(event.fundingTime, -45));
    const defaultExit = toLocalInputValue(offsetIso(event.fundingTime, 90));
    return `
        <div class="action-card primary">
            <p class="helper-text">${t("event_arm_helper")}</p>
            ${event.fundingRatePct != null ? `<div class="chip-row" style="margin-bottom:8px"><span class="chip chip-muted" title="${t("card_rate")}">${t("event_funding_rate")} ${Number(event.fundingRatePct) >= 0 ? "+" : ""}${formatDecimal(event.fundingRatePct, 6)}%</span></div>` : ""}
            <form class="drawer-form" data-action="arm-event" data-id="${event.id}">
                <fieldset class="form-group">
                    <legend>${t("event_entry_window")}</legend>
                    <div class="drawer-form-row labeled-row">
                        <label class="field">
                            <span>${t("event_notional_usd")}</span>
                            <input name="notionalUsd" type="number" step="0.01" placeholder="25" value="${suggestedNotional}">
                        </label>
                        <label class="field">
                            <span>${t("event_side")}</span>
                            <input name="intendedSide" type="text" value="SHORT" readonly>
                            <small>${t("event_short_only")}</small>
                        </label>
                    </div>
                    <div class="drawer-form-row labeled-row">
                        <label class="field">
                            <span>${t("event_planned_entry")}</span>
                            <input name="plannedEntryAt" type="datetime-local" step="0.001" value="${escapeHtml(defaultEntry)}">
                        </label>
                        <label class="field">
                            <span>${t("event_entry_attempts")}</span>
                            <input name="entryAttemptCount" type="number" min="1" max="25" step="1" value="3">
                        </label>
                    </div>
                    <div class="drawer-form-row labeled-row">
                        <label class="field">
                            <span>${t("event_spacing_ms")}</span>
                            <input name="entrySpacingMs" type="number" min="0" step="1" value="150">
                        </label>
                        <label class="field">
                            <span>${t("event_manual_latency")}</span>
                            <input name="manualLatencyAdjustmentMs" type="number" min="-60000" max="60000" step="1" value="0">
                            <small>${t("event_engine_triggers_earlier")}</small>
                        </label>
                    </div>
                </fieldset>
                <fieldset class="form-group">
                    <legend>${t("event_exit_window")}</legend>
                    <label class="field">
                        <span>${t("event_planned_exit")}</span>
                        <input name="plannedExitAt" type="datetime-local" step="0.001" value="${escapeHtml(defaultExit)}">
                    </label>
                </fieldset>
                <fieldset class="form-group">
                    <legend>${t("event_risk_management")}</legend>
                    <div class="drawer-form-row labeled-row">
                        <label class="field">
                            <span>${t("event_stop_loss")}</span>
                            <input name="stopLossUsd" type="number" step="0.01" min="0" placeholder="${t("event_stop_loss_placeholder")}">
                            <small>${t("event_stop_loss_note")}</small>
                        </label>
                        <label class="field">
                            <span>${t("event_take_profit")}</span>
                            <input name="takeProfitUsd" type="number" step="0.01" min="0" placeholder="${t("event_take_profit_placeholder")}">
                            <small>${t("event_take_profit_note")}</small>
                        </label>
                    </div>
                </fieldset>
                <label class="field">
                    <span>${t("event_prep_note")}</span>
                    <textarea name="notes" placeholder="${t("event_prep_note_placeholder")}"></textarea>
                </label>
                <div class="actions">
                    <button class="button" type="submit">${t("event_create_trade")}</button>
                </div>
            </form>
        </div>
    `;
}

export function buildEventExpansionContent({ event, candidate = null, liquidity = null, trade = null, attempts = [], tradeLiquidity = null, outcome = null, position = null }) {
    const canArm = event.status === "DISCOVERED";
    const suggestedNotional = liquidity?.recommendedMaxOrderNotional != null
        ? Math.floor(Number(liquidity.recommendedMaxOrderNotional))
        : 25;

    return `
        ${signalAnalysisChips(candidate, liquidity)}
        ${canArm ? buildArmForm(event, suggestedNotional) : ""}
        ${trade ? buildTradeParamsSection(trade) : ""}
        ${trade ? buildLatencyChainSection(trade) : ""}
        ${buildTradeLiquiditySection(tradeLiquidity)}
        ${buildAttemptsSection(attempts)}
        ${buildPositionSection(position)}
        ${buildOutcomeSection(outcome)}
        ${event.signalCandidateId ? buildDeleteCandidateSection({ id: event.signalCandidateId }, t("event_delete_source")) : ""}
    `;
}

export function buildEventDrawerContent({ event, journal, candidate = null, liquidity = null, trade = null, attempts = [], tradeLiquidity = null, outcome = null, position = null }) {
    const suggestedNotional = liquidity?.recommendedMaxOrderNotional != null
        ? Math.floor(Number(liquidity.recommendedMaxOrderNotional))
        : 25;
    const canArm = event.status === "DISCOVERED";

    return `
        ${pipelineStageMarkup("event")}
        ${section(t("event_snapshot"), `
            <div class="meta-grid">
                ${metaRow(t("event_status"), formatBadge("event", event.status))}
                ${metaRow(t("event_funding_time"), formatInstant(event.fundingTime), formatFundingCountdown(event.fundingTime))}
                ${metaRow(t("event_funding_rate"), formatDecimal(event.fundingRatePct, 6))}
                ${metaRow(t("event_venue"), escapeHtml(event.venue))}
                ${metaRow(t("event_source"), escapeHtml(sourceLabel(event.sourceType)))}
                ${metaRow(t("event_linked_signal"), event.signalCandidateId ? `#${event.signalCandidateId}` : t("label_manual"))}
                ${event.armedTradeId
                    ? metaRow(t("event_prepared_trade"), `<button class="button secondary small" type="button" data-open-trade="${event.armedTradeId}">→ Trade #${event.armedTradeId}</button>`)
                    : metaRow(t("event_prepared_trade"), "—")}
            </div>
            ${signalAnalysisChips(candidate, liquidity)}
        `)}
        ${canArm ? section(t("event_arm_title"), buildArmForm(event, suggestedNotional)) : ""}
        ${trade ? buildTradeParamsSection(trade) : ""}
        ${trade ? buildLatencyChainSection(trade) : ""}
        ${buildTradeLiquiditySection(tradeLiquidity)}
        ${buildAttemptsSection(attempts)}
        ${buildPositionSection(position)}
        ${buildOutcomeSection(outcome)}
        ${event.signalCandidateId ? buildDeleteCandidateSection({ id: event.signalCandidateId }, t("event_delete_source")) : ""}
        ${section("Journal", journalMarkup(journal))}
    `;
}

export async function openEventDetail({ id, nodes, showError }) {
    try {
        const [event, journal] = await Promise.all([
            api.getFundingEvent(id),
            api.listFundingEventJournal(id)
        ]);

        let candidate = null, liquidity = null;
        if (event.signalCandidateId) {
            [candidate, liquidity] = await Promise.all([
                api.getCandidate(event.signalCandidateId).catch(() => null),
                api.getCandidateLiquidity(event.signalCandidateId)
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

        nodes.modalType.textContent = t("event_modal_type");
        nodes.modalTitle.innerHTML = `${venueIcon(event.venue)}${escapeHtml(event.symbol)} · ${escapeHtml(event.venue)}`;
        nodes.modalContent.innerHTML = buildEventDrawerContent({ event, journal, candidate, liquidity, trade, attempts, tradeLiquidity, outcome, position });
        openModal(nodes);
    } catch (error) {
        showError(error.message);
    }
}
