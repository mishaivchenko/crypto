import { api } from "../../api.js";
import {
    emptyState,
    escapeHtml,
    formatBadge,
    formatDecimal,
    formatDurationMs,
    formatInstant,
    formatNumber,
    formatRelative,
    metaRow,
    summaryCard,
    venueCard,
    wireOpenButtons,
    numberOrNull
} from "../shared.js";
import { t } from "../../i18n.js";
import { renderPipelineViz, wirePipelineVizClicks } from "../components/pipeline-viz.js";

// T-24: Dashboard — PipelineViz replaces flat summary cards
export function dashboardPipelineVizMarkup(overview, pnlAggregate) {
    const executedCount = pnlAggregate?.closedTrades ?? 0;

    const stages = [
        {
            label: t("nav_signal_queue") || "Сигналы",
            count: overview.pendingCandidates ?? 0,
            lastDecorator: "+Liquidity",
            decoratorType: "liquidity",
            status: (overview.pendingCandidates ?? 0) > 0 ? "ok" : "neutral",
            screenKey: "candidates"
        },
        {
            label: t("nav_funding_events") || "События",
            count: overview.fundingEvents ?? 0,
            lastDecorator: "+Latency",
            decoratorType: "latency",
            status: (overview.fundingEvents ?? 0) > 0 ? "ok" : "neutral",
            screenKey: "events"
        },
        {
            label: t("nav_prepared_trades") || "Сделки",
            count: overview.armedTrades ?? 0,
            lastDecorator: "+Health",
            decoratorType: "health",
            status: (overview.armedTrades ?? 0) > 0 ? "ok" : "neutral",
            screenKey: "trades"
        },
        {
            label: t("nav_trade_history"),
            count: executedCount,
            lastDecorator: "+Orders",
            decoratorType: "execution",
            status: executedCount > 0 ? "ok" : "neutral",
            screenKey: "history"
        },
    ];

    return { stages, html: renderPipelineViz(stages) };
}

// T-23: Dashboard — Enrichment Freshness KPI card
export function dashboardFreshnessCardMarkup(overview) {
    const freshness = overview.enrichmentFreshness;
    if (!freshness) return "";

    const avgSec = freshness.avgSecondsSinceLastAssessment ?? null;
    let tone, valueLabel;
    if (avgSec == null) {
        tone = "neutral";
        valueLabel = "—";
    } else if (avgSec < 180) {
        tone = "good";
        const m = Math.floor(avgSec / 60);
        const s = Math.round(avgSec % 60);
        valueLabel = m > 0 ? `${m}м ${s}с` : `${s}с`;
    } else if (avgSec < 600) {
        tone = "warning";
        const m = Math.floor(avgSec / 60);
        const s = Math.round(avgSec % 60);
        valueLabel = `${m}м ${s}с`;
    } else {
        tone = "bad";
        const m = Math.floor(avgSec / 60);
        valueLabel = `${m}м`;
    }

    const missingCount = freshness.uncoveredEntityCount ?? 0;

    return summaryCard("Свежесть обогащения", valueLabel, missingCount > 0 ? `${missingCount} без ликвидности` : "Все слои актуальны", tone, true);
}

// T-25: Dashboard — Critical Enrichment Alerts section
export function dashboardEnrichmentAlertsMarkup(candidates, trades, overview) {
    const alerts = [];

    // Alert 1: trades with Latency Chain > 600ms
    // (stale-liquidity alert on candidates omitted — CandidateListItemResponse has no liquidity/sampledAt fields)
    if (trades && trades.length > 0) {
        const highLatCount = trades.filter((tr) => {
            const eff = tr.effectiveEntryLatencyMs ?? tr.measuredEntryLatencyMs ?? null;
            return eff != null && eff > 600;
        }).length;
        if (highLatCount > 0) {
            alerts.push({
                text: `${highLatCount} сделок с Latency Chain > 600мс`,
                screen: "trades",
                tone: "bad"
            });
        }
    }

    // Alert 3: venues with failed connection status
    if (overview && overview.venues) {
        const failedVenues = overview.venues.filter((v) =>
            v.connectionStatus === "INVALID_CREDENTIALS" || v.connectionStatus === "ERROR"
        );
        if (failedVenues.length > 0) {
            const relatedTrades = trades ? trades.filter((tr) =>
                failedVenues.some((v) => v.venue === tr.venue)
            ).length : 0;
            failedVenues.forEach((v) => {
                alerts.push({
                    text: `Площадка ${escapeHtml(v.venue)} недоступна${relatedTrades > 0 ? ` (влияет на ${relatedTrades} активных сделок)` : ""}`,
                    screen: "venues",
                    tone: "bad"
                });
            });
        }
    }

    if (alerts.length === 0) {
        return `<div class="enrichment-alerts enrichment-alerts--ok" style="display:flex;align-items:center;gap:8px;padding:8px;border-radius:6px;background:rgba(56,161,105,0.08);border:1px solid rgba(56,161,105,0.25)">
            <span style="color:var(--freshness-ok,#38a169)">●</span>
            <span style="font-size:12px;color:#aaa">Все слои обогащения актуальны</span>
        </div>`;
    }

    const alertHtml = alerts.map((alert) =>
        `<div class="enrichment-alert" style="display:flex;align-items:center;gap:8px;padding:6px 8px;border-radius:4px;background:rgba(229,62,62,0.08);cursor:pointer" data-alert-screen="${escapeHtml(alert.screen)}">
            <span style="color:#e53e3e;font-size:11px">✕</span>
            <span style="font-size:12px;flex:1">${escapeHtml(alert.text)}</span>
            <span style="font-size:10px;color:#888">→</span>
        </div>`
    ).join("");

    return `<div class="enrichment-alerts" style="display:flex;flex-direction:column;gap:4px">${alertHtml}</div>`;
}

export function dashboardSummaryMarkup(overview) {
    return `
        ${summaryCard(t("nav_signal_queue"), overview.pendingCandidates, t("dashboard_signal_queue_detail"), "info")}
        ${summaryCard(t("nav_funding_events"), overview.fundingEvents, `${overview.discoveredEvents} ${t("dashboard_not_armed")}`, "warning")}
        ${summaryCard(t("nav_prepared_trades"), overview.armedTrades, t("dashboard_prepared_for_engine"), "good")}
        ${summaryCard(t("topbar_access_mode"), String(overview.globalAccessMode ?? "testnet").toUpperCase(), `${overview.activeVenues} ${t("dashboard_venues_detail")} ${overview.version}`, "neutral", true)}
    `;
}

export function dashboardDevToolsMarkup(runtime, runtimeError) {
    if (runtimeError) {
        return `
            <details>
                <summary class="dev-tools-summary">
                    <h3>${t("dashboard_dev_tools")}</h3>
                    <p class="muted">${t("dashboard_runtime_unavailable")}</p>
                </summary>
                <div class="action-card dev-tool-card">
                    <span class="chip dev-chip">${t("dashboard_dev_tool")}</span>
                    <p class="helper-text">${escapeHtml(runtimeError)}</p>
                    <p class="muted dev-tools-note">${t("dashboard_engine_check")}</p>
                </div>
            </details>
            <div class="dev-tools-persistent-actions">
                <button class="icon-button lab-button" type="button" title="${t("dashboard_dev_test_run")}" aria-label="${t("dashboard_dev_test_run")}" data-action="open-dev-test-run">LAB</button>
                <button class="button secondary" type="button" data-action="run-engine-once">${t("dashboard_run_once")}</button>
            </div>
        `;
    }

    const resultMarkup = runtime ? `
        <div class="meta-grid dev-tools-grid">
            ${metaRow(t("dashboard_loop"), runtime.executionLoopEnabled ? "ON" : "OFF", `${t("dashboard_interval")} ${formatDurationMs(runtime.executionLoopIntervalMs)}`)}
            ${metaRow(t("dev_live_orders"), formatBadge("venue", runtime.liveOrderEnabled ? "ON" : "OFF", runtime.liveOrderEnabled ? "good" : "bad"))}
            ${metaRow(t("dev_kill_switch"), formatBadge("venue", runtime.killSwitchEnabled ? "ON" : "OFF", runtime.killSwitchEnabled ? "bad" : "good"))}
            ${metaRow(t("dashboard_runtime_updated"), formatInstant(runtime.runtimeUpdatedAt))}
            ${metaRow(t("dashboard_scheduled_loop"), runtime.lastRunFinishedAt ? formatInstant(runtime.lastRunFinishedAt) : t("dashboard_not_started"), runtime.lastRunFinishedAt ? `${formatRelative(runtime.lastRunFinishedAt)} · ${formatDurationMs(runtime.lastExecutionRunDurationMs)}` : "")}
            ${metaRow(t("dashboard_loop_result"), `${formatNumber(runtime.lastAttemptsSubmitted)} ${t("dashboard_submitted")} / ${formatNumber(runtime.lastAttemptsSkipped)} ${t("dashboard_skipped")}`, `${formatNumber(runtime.lastPlansScanned)} ${t("dashboard_plans_scanned")}`)}
            ${metaRow(t("dashboard_last_force_run"), runtime.lastForcedRunFinishedAt ? formatInstant(runtime.lastForcedRunFinishedAt) : t("dashboard_not_started"), runtime.lastForcedRunFinishedAt ? `${formatRelative(runtime.lastForcedRunFinishedAt)} · ${formatDurationMs(runtime.lastForcedRunDurationMs)}` : t("dashboard_force_run_hint"))}
            ${metaRow(t("dashboard_force_run_result"), `${formatNumber(runtime.lastForcedAttemptsSubmitted)} ${t("dashboard_submitted")} / ${formatNumber(runtime.lastForcedAttemptsSkipped)} ${t("dashboard_skipped")}`, `${formatNumber(runtime.lastForcedPlansScanned)} ${t("dashboard_plans_scanned")}`)}
        </div>
        <form class="drawer-form" data-action="update-engine-runtime">
            <div class="drawer-form-row labeled-row">
                <label class="field">
                    <span>${t("dashboard_exec_loop")}</span>
                    <select name="executionLoopEnabled">
                        <option value="true" ${runtime.executionLoopEnabled ? "selected" : ""}>${t("dashboard_enabled")}</option>
                        <option value="false" ${!runtime.executionLoopEnabled ? "selected" : ""}>${t("dashboard_disabled")}</option>
                    </select>
                </label>
                <label class="field">
                    <span>${t("dashboard_interval_ms")}</span>
                    <input name="executionLoopIntervalMs" type="number" min="${escapeHtml(runtime.minimumExecutionLoopIntervalMs)}" step="50" value="${escapeHtml(runtime.executionLoopIntervalMs)}">
                </label>
            </div>
            <div class="drawer-form-row labeled-row">
                <label class="field">
                    <span>${t("dev_live_orders")}</span>
                    <select name="liveOrderEnabled">
                        <option value="true" ${runtime.liveOrderEnabled ? "selected" : ""}>${t("dashboard_enabled")}</option>
                        <option value="false" ${!runtime.liveOrderEnabled ? "selected" : ""}>${t("dashboard_disabled")}</option>
                    </select>
                </label>
                <label class="field">
                    <span>${t("dev_kill_switch")}</span>
                    <select name="killSwitchEnabled">
                        <option value="true" ${runtime.killSwitchEnabled ? "selected" : ""}>${t("dev_kill_switch_active")}</option>
                        <option value="false" ${!runtime.killSwitchEnabled ? "selected" : ""}>${t("dev_kill_switch_off")}</option>
                    </select>
                </label>
            </div>
            <div class="actions">
                <button class="button secondary" type="submit">${t("dashboard_apply_runtime")}</button>
            </div>
        </form>
        <p class="muted dev-tools-note">${t("dashboard_runtime_note")}</p>
    ` : `
        <p class="muted dev-tools-note">${t("dashboard_dev_tool_note")}</p>
    `;

    return `
        <details>
            <summary class="dev-tools-summary">
                <h3>${t("dashboard_dev_tools")}</h3>
                <p class="muted">${t("dashboard_engineering_note")}</p>
            </summary>
            <div class="action-card dev-tool-card">
                <span class="chip dev-chip">${t("dashboard_dev_tool")}</span>
                ${resultMarkup}
            </div>
        </details>
        <div class="dev-tools-persistent-actions">
            <button class="icon-button lab-button" type="button" title="${t("dashboard_dev_test_run")}" aria-label="${t("dashboard_dev_test_run")}" data-action="open-dev-test-run">LAB</button>
            <button class="button secondary" type="button" data-action="run-engine-once">${t("dashboard_run_once")}</button>
        </div>
    `;
}

export function criticalMetricsPanelMarkup(metrics, pnl) {
    const venueSubmitRows = metrics
        ? Object.entries(metrics.averageSubmitDurationMsByVenue ?? {}).map(([venue, avg]) => {
            const last = metrics.lastSubmitDurationMsByVenue?.[venue] ?? null;
            const avgTone = avg > 500 ? "chip-warning" : "chip-muted";
            const avgValue = `<span class="chip ${avgTone}">${formatDurationMs(avg)}</span>`;
            return metaRow(escapeHtml(venue), avgValue, last != null ? `${t("metrics_last")} ${formatDurationMs(last)}` : "");
        }).join("")
        : "";

    const exchangeLatencySection = venueSubmitRows
        ? `<div class="meta-grid">${venueSubmitRows}</div>`
        : `<p class="muted">${t("empty_engine_no_latency")}</p>`;

    const planFetchTone = metrics && metrics.averagePlanFetchDurationMs > 200 ? "chip-warning" : "chip-muted";
    const internalTimingSection = metrics ? `
        <div class="meta-grid">
            ${metaRow(t("metrics_plan_fetch"), `<span class="chip ${planFetchTone}">${formatDurationMs(metrics.averagePlanFetchDurationMs)}</span>`, `${t("metrics_last")} ${formatDurationMs(metrics.lastPlanFetchDurationMs)}`)}
            ${metaRow(t("metrics_attempt_record"), formatDurationMs(metrics.averageAttemptRecordDurationMs), `${t("metrics_last")} ${formatDurationMs(metrics.lastAttemptRecordDurationMs)}`)}
            ${metaRow(t("metrics_execution_run"), formatDurationMs(metrics.averageExecutionRunDurationMs), `${t("metrics_last")} ${formatDurationMs(metrics.lastExecutionRunDurationMs)}`)}
        </div>
    ` : `<p class="muted">${t("empty_engine_no_data")}</p>`;

    const pnlSection = pnl ? (() => {
        const net = Number(pnl.totalNetPnlUsd ?? 0);
        const netTone = net >= 0 ? "chip-good" : "chip-bad";
        const netValue = `<span class="chip ${netTone}">${net >= 0 ? "+" : ""}${formatDecimal(net, 4)} USD</span>`;
        const winRate = pnl.closedTrades > 0 ? pnl.profitableTrades / pnl.closedTrades : null;
        const winTone = winRate == null ? "chip-muted" : winRate >= 0.6 ? "chip-good" : winRate >= 0.4 ? "chip-warning" : "chip-bad";
        const winValue = winRate != null
            ? `<span class="chip ${winTone}">${formatNumber(pnl.profitableTrades)}/${formatNumber(pnl.closedTrades)} (${Math.round(winRate * 100)}%)</span>`
            : `<span class="chip chip-muted">${formatNumber(pnl.closedTrades)} ${t("metrics_closed_trades_short")}</span>`;
        return `
            <div class="meta-grid">
                ${metaRow(t("metrics_net_pnl"), netValue)}
                ${metaRow(t("metrics_gross_pnl"), `${formatDecimal(pnl.totalGrossPnlUsd, 4)} USD`)}
                ${metaRow(t("metrics_total_fees"), `${formatDecimal(pnl.totalFeesUsd, 4)} USD`)}
                ${metaRow(t("metrics_win_rate"), winValue, `${formatNumber(pnl.closedTrades)} ${t("metrics_closed_trades_short")}`)}
            </div>
        `;
    })() : `<p class="muted">${t("empty_engine_no_closed")}</p>`;

    return `
        <div class="panel-header">
            <h3>${t("metrics_critical")}</h3>
        </div>
        <details open>
            <summary class="meta-label">${t("metrics_exchange_submit")}</summary>
            ${exchangeLatencySection}
        </details>
        <details>
            <summary class="meta-label">${t("metrics_engine_timing")}</summary>
            ${internalTimingSection}
        </details>
        <details>
            <summary class="meta-label">${t("metrics_pnl_summary")}</summary>
            ${pnlSection}
        </details>
    `;
}

export function renderDashboard({ nodes, overview, state, onOpenVenue, onNavigate }) {
    nodes.globalModeSelect.value = String(overview.globalAccessMode ?? "TESTNET").toUpperCase();

    // T-24: PipelineViz replaces flat summary cards + keep access-mode card
    const { stages, html: pipelineHtml } = dashboardPipelineVizMarkup(overview, state.pnlAggregate);
    const accessModeCard = summaryCard(
        t("topbar_access_mode"),
        String(overview.globalAccessMode ?? "testnet").toUpperCase(),
        `${overview.activeVenues} ${t("dashboard_venues_detail")} ${overview.version}`,
        "neutral",
        true
    );

    // T-23: Freshness KPI card
    const freshnessCard = dashboardFreshnessCardMarkup(overview);

    // T-25: Critical Enrichment Alerts — computed before innerHTML assignment
    const alertsHtml = dashboardEnrichmentAlertsMarkup(
        state.lastCandidates ?? null,
        state.lastTrades ?? null,
        overview
    );

    nodes.dashboardSummary.innerHTML = `
        <div class="snapshot-row" style="display:flex;align-items:flex-start;gap:12px;flex-wrap:wrap">
            <div style="flex:1;min-width:0">${pipelineHtml}</div>
            <div class="snapshot-status-cards" style="display:flex;gap:8px;flex-wrap:wrap;align-items:flex-start">
                ${accessModeCard}
                ${freshnessCard}
            </div>
        </div>
        ${alertsHtml ? `<div class="snapshot-alerts" style="margin-top:8px">${alertsHtml}</div>` : ""}
    `;

    // Wire PipelineViz navigation clicks (T-24)
    const pipelineEl = nodes.dashboardSummary.querySelector(".pipeline-viz");
    if (pipelineEl && onNavigate) {
        const stagesWithNav = stages.map((s) => ({ ...s, onClick: () => onNavigate(s.screenKey) }));
        wirePipelineVizClicks(pipelineEl, stagesWithNav);
    }

    // Wire alert clicks (alerts now inside innerHTML)
    nodes.dashboardSummary.querySelectorAll("[data-alert-screen]").forEach((el) => {
        el.addEventListener("click", () => {
            if (onNavigate) onNavigate(el.dataset.alertScreen);
        });
    });

    nodes.dashboardMetrics.innerHTML = criticalMetricsPanelMarkup(state.engineMetrics, state.pnlAggregate);
    nodes.dashboardVenues.innerHTML = overview.venues.length
        ? overview.venues.map((venue) => venueCard(venue)).join("")
        : emptyState(t("empty_venue_diagnostics"), t("empty_venue_diagnostics_detail"));

    wireOpenButtons(nodes.dashboardVenues, "[data-open-venue]", onOpenVenue);
}

export async function handleRunEngineOnce({ state, refreshCurrentScreen, showSuccess, showError }) {
    try {
        const result = await api.runEngineOnce(true);
        state.lastEngineRun = result;
        await refreshCurrentScreen();
        showSuccess(`${t("app_engine_run_completed")} ${result.plansScanned}, ${t("app_engine_run_submitted")} ${result.attemptsSubmitted}, ${t("app_engine_run_skipped")} ${result.attemptsSkipped}.`);
    } catch (error) {
        showError(error.message);
    }
}

export async function handleUpdateEngineRuntime({ event, state, refreshCurrentScreen, showSuccess, showError }) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    try {
        state.engineRuntime = await api.updateEngineRuntime({
            executionLoopEnabled: data.get("executionLoopEnabled") === "true",
            executionLoopIntervalMs: numberOrNull(data.get("executionLoopIntervalMs")),
            liveOrderEnabled: data.get("liveOrderEnabled") === "true",
            killSwitchEnabled: data.get("killSwitchEnabled") === "true"
        });
        state.engineRuntimeError = null;
        await refreshCurrentScreen();
        showSuccess(`${t("app_engine_runtime_updated")} ${state.engineRuntime.executionLoopEnabled ? "ON" : "OFF"}, ${t("app_engine_interval")} ${state.engineRuntime.executionLoopIntervalMs} ${t("app_engine_ms")}.`);
    } catch (error) {
        showError(error.message);
    }
}
