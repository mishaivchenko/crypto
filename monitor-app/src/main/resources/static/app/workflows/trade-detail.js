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
    toLocalInputValueSeconds,
    venueIcon
} from "../shared.js";
import { t } from "../../i18n.js";
import { buildDeleteCandidateSection } from "./pipeline.js";
import { renderLayerBlock, getLayerCollapsed } from "../components/layer-block.js";
import { renderEnrichmentTimeline } from "../components/enrichment-timeline.js";

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

// T-16: Latency Chain LayerBlock for expanded trade card
function buildLatencyLayerBlock(trade) {
    const p50 = trade.warmupP50Ms ?? trade.measuredEntryLatencyMs;
    const manualAdj = trade.manualLatencyAdjustmentMs ?? 0;
    const effectiveLead = trade.effectiveEntryLatencyMs ?? 0;
    const p50Label = p50 != null ? `${p50}ms` : "—";
    const adjLabel = formatSignedMs(manualAdj);

    const latStatus = !p50 ? "missing"
        : effectiveLead > 600 || !trade.warmupDoneAt ? "blocked"
        : effectiveLead > 400 ? "warn"
        : "ok";

    const warmupP50Row = trade.warmupP50Ms != null
        ? `<div style="display:flex;gap:16px;font-size:11px;margin-top:4px">
             <span>p50: <strong>${trade.warmupP50Ms}ms</strong></span>
             ${trade.warmupP95Ms != null ? `<span>p95: <strong>${trade.warmupP95Ms}ms</strong></span>` : ""}
             ${trade.warmupFallbackUsed ? `<span class="chip chip-warning" style="font-size:10px">Fallback</span>` : ""}
           </div>`
        : "";

    const latContent = `
        <div class="latency-summary" style="margin-bottom:6px">
            <span class="formula-item">${escapeHtml(p50Label)} p50</span>
            <span class="formula-op">+</span>
            <span class="formula-item">${escapeHtml(adjLabel)} adj</span>
            <span class="formula-op">=</span>
            <strong class="formula-item formula-result">${effectiveLead}ms ${t("trade_effective_trigger")}</strong>
        </div>
        ${warmupP50Row}
        <div class="kv-row" style="margin-top:6px">
            ${metaRow(t("trade_measured_latency"), formatDurationMs(trade.measuredEntryLatencyMs))}
            ${trade.warmupDoneAt ? metaRow("Warmup done", formatInstant(trade.warmupDoneAt)) : ""}
        </div>
        ${!trade.warmupDoneAt ? `<p class="muted" style="font-size:11px;margin-top:4px">Warmup не завершён</p>` : ""}
    `;

    return renderLayerBlock({
        layerType: "latency",
        layerName: t("event_latency_chain"),
        decoratorName: "WARMUP_PROBE",
        timestamp: trade.warmupDoneAt,
        source: "WARMUP_PROBE",
        status: latStatus,
        collapsed: getLayerCollapsed("trade", "latency", true),
        screen: "trade",
        content: latContent
    });
}

// T-17: Layer-aware action bar for expanded trade card
function buildLayerAwareActionBar(trade, liquidity) {
    if (trade.state !== "ARMED") return "";

    const effLat = trade.effectiveEntryLatencyMs ?? trade.measuredEntryLatencyMs ?? null;
    const liqBlocked = liquidity && (liquidity.score === "UNTRADABLE" || liquidity.score === "THIN");
    const latBlocked = effLat != null && effLat > 600;
    const engineDisabled = liqBlocked || latBlocked;

    let engineTooltip = "";
    if (liqBlocked) {
        engineTooltip = `Liquidity Layer: ${escapeHtml(liquidity.score)}`;
    } else if (latBlocked) {
        engineTooltip = `Latency Layer: ${effLat}ms > 600ms`;
    }

    const venue = trade.venueSymbol ?? trade.symbol ?? "";

    return `
        <div class="action-row layer-aware-action-bar" style="display:flex;gap:8px;flex-wrap:wrap;margin:8px 0">
            <button class="button" type="button" data-arm-engine="${trade.id}"
                ${engineDisabled ? "disabled" : ""}
                ${engineTooltip ? `title="${escapeHtml(engineTooltip)}"` : ""}>
                ${t("trade_arm_engine") || "Запустить Engine"}
            </button>
            <button class="chip chip-btn" type="button"
                data-refresh-liquidity="${trade.id}"
                data-venue="${escapeHtml(trade.venue ?? "")}"
                data-symbol="${escapeHtml(venue)}"
                title="Latency пересчитывается только при пересоздании сделки">
                ${t("trade_recalculate_enrichment") || "Пересчитать обогащение"}
            </button>
            <button class="btn-danger-small" type="button" data-cancel-trade="${trade.id}">
                ✕ ${t("trade_cancel_button")}
            </button>
        </div>
    `;
}

export function buildTradeExpansionContent({ trade, attempts = [], liquidity = null, position = null, outcome = null }) {
    return `
        ${buildLiquiditySection(liquidity, trade)}
        ${buildLatencyLayerBlock(trade)}
        ${buildLayerAwareActionBar(trade, liquidity)}
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
                                <input name="plannedEntryAt" type="datetime-local" step="1" value="${escapeHtml(toLocalInputValueSeconds(trade.plannedEntryAt) ?? "")}">
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
                            <input name="plannedExitAt" type="datetime-local" step="1" value="${escapeHtml(toLocalInputValueSeconds(trade.plannedExitAt) ?? "")}">
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

// T-18: Build EnrichmentTimeline layers from trade data (+ optional candidate + attempts + liquidity)
function buildTradeEnrichmentTimelineLayers(trade, candidate, attempts, liquidity) {
    const layers = [];

    // Step 1: Base Signal
    layers.push({
        name: "Base Signal",
        status: candidate ? "ok" : "missing",
        timestamp: candidate?.detectedAt ?? null,
        decorator: "FUNDING_API",
        details: candidate ? `Signal #${candidate.id}` : "Источник сигнала не загружен"
    });

    // Step 2: +Liquidity — uses the separately-loaded liquidity object, not a nested trade field
    const liqTs = liquidity?.sampledAt ?? null;
    const liqStatus = !liqTs ? "missing"
        : liquidity?.score === "UNTRADABLE" ? "blocked"
        : liquidity?.score === "THIN" ? "warn"
        : "ok";
    layers.push({
        name: "+Liquidity",
        status: liqStatus,
        timestamp: liqTs,
        decorator: "ORDER_BOOK",
        details: liqTs ? `Score: ${escapeHtml(liquidity?.score ?? "—")}` : "Нет данных ликвидности"
    });

    // Step 3: +Latency Chain
    const warmupTs = trade.warmupDoneAt ?? null;
    const warmupStatus = !warmupTs ? "missing" : trade.warmupFallbackUsed ? "warn" : "ok";
    layers.push({
        name: "+Latency Chain",
        status: warmupStatus,
        timestamp: warmupTs,
        decorator: "WARMUP_PROBE",
        details: warmupTs ? `Effective: ${trade.effectiveEntryLatencyMs ?? "—"}ms` : "Warmup не завершён"
    });

    // Step 4: Armed
    layers.push({
        name: "Armed",
        status: trade.armedAt ? "ok" : "missing",
        timestamp: trade.armedAt ?? null,
        decorator: trade.armSource ?? "OPERATOR",
        details: trade.armSource ? `Source: ${escapeHtml(trade.armSource)}` : ""
    });

    // Step 5: Execution (optional)
    if (attempts && attempts.length > 0) {
        const firstAttempt = attempts[0];
        const execStatus = firstAttempt.status === "FILLED" ? "ok"
            : firstAttempt.status === "FAILED" ? "blocked"
            : "warn";
        layers.push({
            name: "Execution",
            status: execStatus,
            timestamp: firstAttempt.triggerAt ?? null,
            decorator: "ENGINE",
            details: `${escapeHtml(firstAttempt.status)} · ${escapeHtml(firstAttempt.failureReason ?? "")}`
        });
    }

    return layers;
}

// T-19/T-20: Build Base Layer block
function buildBaseLayerBlock(trade, candidate) {
    const baseTs = candidate?.detectedAt ?? null;
    const baseStatus = candidate ? "ok" : "missing";
    const rawRate = candidate?.fundingRate != null ? `${formatDecimal(candidate.fundingRate * 100, 4)}%` : "—";
    const content = `
        <div class="kv-row">
            ${metaRow(t("trade_instrument"), escapeHtml(trade.symbol ?? "—"))}
            ${metaRow(t("trade_venue"), escapeHtml(trade.venue ?? "—"))}
            ${metaRow(t("trade_funding_event"), `#${trade.fundingEventId}`)}
            ${metaRow(t("trade_funding_time"), formatInstant(trade.fundingTime), formatFundingCountdown(trade.fundingTime))}
            ${metaRow(t("trade_source_signal"), trade.signalCandidateId ? `#${trade.signalCandidateId}` : t("label_manual"))}
        </div>
        <div class="chip-row" style="margin-top:6px">
            ${formatBadge("trade", trade.state)}
            <span class="chip chip-muted">${formatDecimal(trade.notionalUsd, 2)} USD</span>
            <span class="chip chip-muted">${escapeHtml(sideLabel(trade.intendedSide))}</span>
            ${trade.mode ? `<span class="chip ${trade.mode === "testnet" ? "chip-warning" : "chip-muted"}">${escapeHtml(modeLabel(trade.mode))}</span>` : ""}
            ${rawRate !== "—" ? `<span class="chip chip-muted">${t("trade_raw_rate") || "Rate"}: ${rawRate}</span>` : ""}
        </div>
    `;
    return renderLayerBlock({
        layerType: "base",
        layerName: t("layer.base"),
        decoratorName: "FundingApiCandidateSourceService",
        timestamp: baseTs,
        source: "RSS/API",
        status: baseStatus,
        collapsed: getLayerCollapsed("trade", "base", false),
        screen: "trade",
        content
    });
}

// T-19/T-20: Build Liquidity Layer block
function buildLiquidityLayerBlock(liquidity, trade) {
    const effectiveSymbol = trade.venueSymbol ?? trade.symbol;
    const assessBtn = (trade.venue && effectiveSymbol)
        ? `<button class="chip chip-btn" type="button" data-assess-liquidity="${trade.id}" data-venue="${escapeHtml(trade.venue)}" data-symbol="${escapeHtml(effectiveSymbol)}">${t("liquidity_assess_button")}</button>`
        : "";
    const refreshBtn = (trade.venue && effectiveSymbol)
        ? `<button class="chip chip-btn" type="button" data-refresh-liquidity="${trade.id}" data-venue="${escapeHtml(trade.venue)}" data-symbol="${escapeHtml(effectiveSymbol)}">${t("liquidity_refresh_button")}</button>`
        : "";

    const liqTs = liquidity?.sampledAt ?? null;
    const liqStatus = !liquidity ? "missing"
        : liquidity.score === "UNTRADABLE" ? "blocked"
        : liquidity.score === "THIN" ? "warn"
        : "ok";

    let content;
    if (!liquidity) {
        content = `<p class="muted">${t("liquidity_no_assessment")}</p><div class="chip-row" style="margin-top:8px">${assessBtn}</div>`;
    } else {
        const scoreTone = liquidity.score === "EXCELLENT" || liquidity.score === "GOOD" ? "good"
            : liquidity.score === "THIN" || liquidity.score === "UNTRADABLE" ? "bad" : "warning";
        const warning = liquidity.score === "UNTRADABLE"
            ? `<div class="banner">${t("liquidity_warning_untradable")}</div>`
            : liquidity.score === "THIN"
                ? `<div class="banner" style="border-color:rgba(255,190,60,0.22);background:linear-gradient(180deg,rgba(58,44,10,0.96),rgba(36,27,6,0.94))">${t("liquidity_warning_thin")}</div>`
                : "";
        content = `
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
        `;
    }

    return renderLayerBlock({
        layerType: "liquidity",
        layerName: t("layer.liquidity"),
        decoratorName: "LiquidityAssessmentService",
        timestamp: liqTs,
        source: "ORDER_BOOK_PROBE",
        status: liqStatus,
        collapsed: getLayerCollapsed("trade", "liquidity", false),
        screen: "trade",
        content
    });
}

// T-19/T-20: Build Latency Chain Layer block (replaces buildLatencyLayerBlock)
function buildLatencyChainLayerBlock(trade) {
    const p50 = trade.warmupP50Ms ?? trade.measuredEntryLatencyMs;
    const manualAdj = trade.manualLatencyAdjustmentMs ?? 0;
    const effectiveLead = trade.effectiveEntryLatencyMs ?? 0;
    const p50Label = p50 != null ? `${p50}ms` : "—";
    const adjLabel = formatSignedMs(manualAdj);

    const latStatus = !p50 ? "missing"
        : effectiveLead > 600 || !trade.warmupDoneAt ? "blocked"
        : effectiveLead > 400 ? "warn"
        : "ok";

    const warmupP50Row = trade.warmupP50Ms != null
        ? `<div style="display:flex;gap:16px;font-size:11px;margin-top:4px">
             <span>p50: <strong>${trade.warmupP50Ms}ms</strong></span>
             ${trade.warmupP95Ms != null ? `<span>p95: <strong>${trade.warmupP95Ms}ms</strong></span>` : ""}
             ${trade.warmupFallbackUsed ? `<span class="chip chip-warning" style="font-size:10px">Fallback</span>` : ""}
           </div>`
        : "";

    const content = `
        <div class="latency-summary" style="margin-bottom:6px">
            <span class="formula-item">${escapeHtml(p50Label)} p50 ${infoTip(t("tip_latency_p50"))}</span>
            <span class="formula-op">+</span>
            <span class="formula-item">${escapeHtml(adjLabel)} adj ${infoTip(t("tip_latency_adj"))}</span>
            <span class="formula-op">=</span>
            <strong class="formula-item formula-result">${effectiveLead}ms ${t("trade_effective_trigger")} ${infoTip(t("tip_latency_effective"))}</strong>
        </div>
        ${warmupP50Row}
        <div class="kv-row" style="margin-top:6px">
            ${metaRow(t("trade_measured_latency"), formatDurationMs(trade.measuredEntryLatencyMs))}
            ${metaRow(t("trade_armed_at"), formatInstant(trade.armedAt))}
            ${metaRow(t("trade_entry_lead"), formatDurationMs(trade.entryLeadMs))}
            ${metaRow(t("trade_exit_lead"), formatDurationMs(trade.exitLeadMs))}
            ${trade.warmupDoneAt ? metaRow("Warmup done", formatInstant(trade.warmupDoneAt)) : ""}
            ${trade.armSource ? metaRow(t("trade_arm_source"), escapeHtml(trade.armSource)) : ""}
        </div>
        ${!trade.warmupDoneAt ? `<p class="muted" style="font-size:11px;margin-top:4px">${t("warmup_not_done") || "Warmup не завершён"}</p>` : ""}
        ${trade.warmupFallbackUsed ? `<p class="warmup-warning" style="margin-top:4px">${t("warmup_fallback_warning")}</p>` : ""}
    `;

    return renderLayerBlock({
        layerType: "latency",
        layerName: t("layer.latency"),
        decoratorName: "VenueLatencyService",
        timestamp: trade.warmupDoneAt,
        source: "WARMUP_PROBE",
        status: latStatus,
        collapsed: getLayerCollapsed("trade", "latency", true),
        screen: "trade",
        content
    });
}

// T-19/T-20: Build Health Layer block
function buildHealthLayerBlock(trade, liquidity) {
    const effLat = trade.effectiveEntryLatencyMs ?? trade.measuredEntryLatencyMs ?? null;
    const liqBlocked = liquidity && (liquidity.score === "UNTRADABLE" || liquidity.score === "THIN");
    const latWarn = effLat != null && effLat > 400;
    const latBlocked = effLat != null && effLat > 600;

    let healthStatus = "ok";
    const recommendations = [];

    if (!liquidity) {
        healthStatus = "missing";
        recommendations.push(t("health_no_liquidity") || "Нет данных ликвидности — оценка не доступна");
    } else if (liqBlocked) {
        healthStatus = "blocked";
        recommendations.push(`${t("health_liquidity_blocked") || "Ликвидность"}: ${escapeHtml(liquidity.score)}`);
    }

    if (latBlocked) {
        healthStatus = "blocked";
        recommendations.push(`${t("health_latency_blocked") || "Высокая задержка"}: ${effLat}мс > 600мс`);
    } else if (latWarn) {
        if (healthStatus === "ok") healthStatus = "warn";
        recommendations.push(`${t("health_latency_warn") || "Повышенная задержка"}: ${effLat}мс > 400мс`);
    }

    if (!trade.warmupDoneAt) {
        if (healthStatus === "ok") healthStatus = "warn";
        recommendations.push(t("health_warmup_missing") || "Warmup не завершён");
    }

    const recHtml = recommendations.length
        ? `<ul style="margin:4px 0 0;padding-left:16px;font-size:12px">${recommendations.map(r => `<li>${r}</li>`).join("")}</ul>`
        : `<p class="muted" style="font-size:12px;margin:4px 0 0">${t("health_all_ok") || "Все показатели в норме"}</p>`;

    const content = `
        <div style="margin-bottom:4px">
            <span class="chip ${healthStatus === "ok" ? "chip-good" : healthStatus === "blocked" ? "chip-bad" : "chip-warning"}" style="font-size:12px">
                ${healthStatus === "ok" ? "✓" : healthStatus === "blocked" ? "✕" : "⚠"} ${t(`layer.status.${healthStatus}`) || healthStatus}
            </span>
        </div>
        ${recHtml}
    `;

    return renderLayerBlock({
        layerType: "health",
        layerName: t("layer.health"),
        decoratorName: "Engine",
        timestamp: trade.armedAt,
        source: "AUTO_COMPUTED",
        status: healthStatus,
        collapsed: getLayerCollapsed("trade", "health", false),
        screen: "trade",
        content
    });
}

// T-19/T-20: Build Execution Layer block (only when attempts present)
function buildExecutionLayerBlock(attempts, position, outcome) {
    if (!attempts || attempts.length === 0) return "";

    const firstAttempt = attempts[0];
    const execStatus = firstAttempt.status === "FILLED" ? "ok"
        : firstAttempt.status === "FAILED" ? "blocked"
        : "warn";

    const attemptsHtml = attempts.map((attempt) => `
        <div class="meta-row">
            <span class="meta-label">#${escapeHtml(String(attempt.attemptNumber ?? "—"))} · ${escapeHtml(attempt.status)}</span>
            <strong class="meta-value">${escapeHtml(attempt.symbol ?? "—")} ${escapeHtml(attempt.side ?? "—")}</strong>
            <span class="meta-helper">
                ${attempt.averageFillPrice != null ? `${t("event_fill_price")} ${formatDecimal(attempt.averageFillPrice, 4)} · ` : ""}
                ${attempt.feeUsd != null ? `${t("event_fees")} ${formatDecimal(attempt.feeUsd, 4)} USD · ` : ""}
                ${escapeHtml(attempt.failureReason ?? t("trade_no_error"))} · ${t("trade_trigger")} ${formatInstant(attempt.triggerAt)} · ${t("trade_recorded")} ${formatInstant(attempt.createdAt)}
            </span>
        </div>
    `).join("");

    const posHtml = position ? `
        <div class="kv-row" style="margin-top:8px">
            ${metaRow(t("event_entry_price"), position.entryPrice != null ? formatDecimal(position.entryPrice, 4) : "—")}
            ${metaRow(t("event_exit_price"), position.exitPrice != null ? formatDecimal(position.exitPrice, 4) : "—")}
            ${metaRow(t("history_quantity"), position.quantity != null ? formatDecimal(position.quantity, 6) : "—")}
            ${metaRow(t("history_opened_at"), formatInstant(position.openedAt))}
            ${position.closedAt ? metaRow(t("history_closed_at"), formatInstant(position.closedAt)) : ""}
        </div>
    ` : "";

    const outcomeHtml = outcome ? (() => {
        const net = outcome.netPnlUsd != null ? Number(outcome.netPnlUsd) : null;
        const netTone = net == null ? "muted" : net >= 0 ? "good" : "bad";
        return `
            <div class="chip-row" style="margin-top:8px;margin-bottom:4px">
                ${net != null ? `<span class="chip chip-${netTone}">${net >= 0 ? "+" : ""}${formatDecimal(net, 4)} USD</span>` : ""}
                ${outcome.outcomeCode ? `<span class="chip chip-muted">${escapeHtml(outcome.outcomeCode)}</span>` : ""}
            </div>
            <div class="kv-row">
                ${metaRow(t("event_gross_pnl"), outcome.grossPnlUsd != null ? `${formatDecimal(outcome.grossPnlUsd, 4)} USD` : "—")}
                ${metaRow(t("event_net_pnl"), net != null ? `${net >= 0 ? "+" : ""}${formatDecimal(net, 4)} USD` : "—")}
                ${metaRow(t("event_fees"), outcome.feesUsd != null ? `${formatDecimal(outcome.feesUsd, 4)} USD` : "—")}
                ${metaRow(t("history_evaluated_at"), formatInstant(outcome.evaluatedAt))}
            </div>
        `;
    })() : "";

    return renderLayerBlock({
        layerType: "execution",
        layerName: t("layer.execution"),
        decoratorName: "EngineExecutionService",
        timestamp: firstAttempt.triggerAt,
        source: "LIVE_ORDER",
        status: execStatus,
        collapsed: getLayerCollapsed("trade", "execution", true),
        screen: "trade",
        content: attemptsHtml + posHtml + outcomeHtml
    });
}

// T-21: Build final verdict block
function buildFinalVerdictBlock(trade, liquidity, attempts) {
    const effLat = trade.effectiveEntryLatencyMs ?? trade.measuredEntryLatencyMs ?? null;

    const blockers = [];
    const warnings = [];

    // Liquidity layer
    if (!liquidity) {
        warnings.push(`? ${t("layer.liquidity")}: ${t("layer.status.missing")}`);
    } else if (liquidity.score === "UNTRADABLE") {
        blockers.push(`❌ ${t("layer.liquidity")}: UNTRADABLE`);
    } else if (liquidity.score === "THIN") {
        warnings.push(`⚠️ ${t("layer.liquidity")}: THIN`);
    }

    // Latency layer
    if (!trade.warmupDoneAt && !effLat) {
        warnings.push(`? ${t("layer.latency")}: ${t("layer.status.missing")}`);
    } else if (effLat != null && effLat > 600) {
        blockers.push(`❌ ${t("layer.latency")}: ${effLat}мс (> 600мс)`);
    } else if (effLat != null && effLat > 400) {
        warnings.push(`⚠️ ${t("layer.latency")}: ${effLat}мс (> 400мс)`);
    }

    // Warmup
    if (!trade.warmupDoneAt && effLat == null) {
        warnings.push(`? ${t("layer.latency")}: Warmup не завершён`);
    }

    // Determine overall state
    let verdictColor, verdictIcon, verdictTitle;
    if (blockers.length > 0) {
        verdictColor = "var(--freshness-missing)";
        verdictIcon = "✕";
        verdictTitle = `${t("verdict.blocked") || "Не готов"} — ${blockers.length} ${t("verdict_blockers_count") || "слоёв блокируют"}`;
    } else if (warnings.length > 0) {
        verdictColor = "var(--freshness-stale)";
        verdictIcon = "⚠";
        verdictTitle = `${t("verdict.warnings") || "Предупреждения"} (${warnings.length})`;
    } else {
        verdictColor = "var(--freshness-ok)";
        verdictIcon = "✓";
        verdictTitle = t("verdict.ready") || "Готов к вводу";
    }

    const issuesList = [...blockers, ...warnings];
    const issuesHtml = issuesList.length
        ? `<ul style="margin:6px 0 0;padding-left:16px;font-size:12px;color:#bbb">${issuesList.map(i => `<li>${escapeHtml(i)}</li>`).join("")}</ul>`
        : "";

    // Aggregate freshness: oldest of all layer timestamps
    const timestamps = [
        liquidity?.sampledAt,
        trade.warmupDoneAt,
        trade.armedAt
    ].filter(Boolean);

    let freshnessHtml = "";
    if (timestamps.length > 0) {
        const oldestTs = timestamps.reduce((a, b) => a < b ? a : b);
        const diffMs = Date.now() - +new Date(oldestTs);
        const diffMin = Math.floor(diffMs / 60000);
        const diffSec = Math.floor(diffMs / 1000);
        const relLabel = diffSec < 60
            ? (diffSec < 5 ? (t("freshness.just_now") || "только что") : `${diffSec}с назад`)
            : `${diffMin}м назад`;
        const freshnessClass = diffSec < 120 ? "freshness-ok" : diffSec < 600 ? "freshness-stale" : "freshness-missing";
        freshnessHtml = `<div style="margin-top:6px;font-size:11px;color:#888">${t("verdict_last_enrichment") || "Последнее обогащение"}: <span class="${freshnessClass}">${escapeHtml(relLabel)}</span></div>`;
    }

    return `
        <div class="verdict-block" style="border:1px solid ${verdictColor};border-radius:6px;padding:10px 12px;margin:12px 0 8px;background:rgba(0,0,0,0.18)">
            <div style="font-size:14px;font-weight:700;color:${verdictColor}">${verdictIcon} ${escapeHtml(verdictTitle)}</div>
            ${issuesHtml}
            ${freshnessHtml}
        </div>
    `;
}

export function buildTradeDrawerContent({ trade, journal, attempts, liquidity, position = null, outcome = null, candidate = null }) {
    const timelineLayers = buildTradeEnrichmentTimelineLayers(trade, candidate, attempts, liquidity);
    const editForm = trade.state === "ARMED" ? `
        ${section(t("trade_edit_title"), `
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
                                <input name="plannedEntryAt" type="datetime-local" step="1" value="${escapeHtml(toLocalInputValueSeconds(trade.plannedEntryAt) ?? "")}">
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
                            <input name="plannedExitAt" type="datetime-local" step="1" value="${escapeHtml(toLocalInputValueSeconds(trade.plannedExitAt) ?? "")}">
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
        `)}
    ` : "";

    return `
        ${pipelineStageMarkup("trade")}
        <div class="trade-enrichment-timeline-section" style="margin-bottom:12px">
            <div class="meta-label" style="margin-bottom:4px;font-size:11px;color:#888">Enrichment Timeline</div>
            ${renderEnrichmentTimeline(timelineLayers)}
        </div>
        ${buildBaseLayerBlock(trade, candidate)}
        ${buildLiquidityLayerBlock(liquidity, trade)}
        ${buildLatencyChainLayerBlock(trade)}
        ${buildHealthLayerBlock(trade, liquidity)}
        ${buildExecutionLayerBlock(attempts, position, outcome)}
        ${buildFinalVerdictBlock(trade, liquidity, attempts)}
        ${buildLayerAwareActionBar(trade, liquidity)}
        ${editForm}
        ${buildCancelSection(trade)}
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

        // T-18: optionally load candidate for timeline step 1
        let candidate = null;
        if (trade.signalCandidateId) {
            candidate = await api.getCandidate(trade.signalCandidateId).catch(() => null);
        }

        nodes.modalType.textContent = t("trade_modal_type");
        nodes.modalTitle.innerHTML = trade.symbol
            ? `${venueIcon(trade.venue)}${escapeHtml(trade.symbol)} · ${escapeHtml(trade.venue)}`
            : escapeHtml(`${t("card_trade_prefix")}${trade.id}`);
        nodes.modalContent.innerHTML = buildTradeDrawerContent({ trade, journal, attempts, liquidity, position, outcome, candidate });
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

        const armEngineBtn = nodes.modalContent.querySelector("[data-arm-engine]");
        if (armEngineBtn) {
            armEngineBtn.addEventListener("click", async () => {
                armEngineBtn.disabled = true;
                armEngineBtn.textContent = "…";
                try {
                    await api.runEngineOnce(true);
                    if (onRefresh) onRefresh();
                    await openTradeDetail({ id, nodes, showError, onRefresh });
                } catch (err) {
                    showError(err.message);
                    armEngineBtn.disabled = false;
                    armEngineBtn.textContent = t("trade_arm_engine") || "Запустить Engine";
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
