import { api } from "../../api.js";
import {
    emptyState,
    escapeHtml,
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
            <div class="panel-header">
            <div>
                <h3>${t("dashboard_dev_tools")}</h3>
                <p class="muted">${t("dashboard_runtime_unavailable")}</p>
            </div>
            <div class="actions">
                <button class="icon-button lab-button" type="button" title="${t("dashboard_dev_test_run")}" aria-label="${t("dashboard_dev_test_run")}" data-action="open-dev-test-run">LAB</button>
                <button class="button secondary" type="button" data-action="run-engine-once">${t("dashboard_run_once")}</button>
            </div>
        </div>
            <div class="action-card dev-tool-card">
                <span class="chip dev-chip">${t("dashboard_dev_tool")}</span>
                <p class="helper-text">${escapeHtml(runtimeError)}</p>
                <p class="muted dev-tools-note">${t("dashboard_engine_check")}</p>
            </div>
        `;
    }

    const resultMarkup = runtime ? `
        <div class="meta-grid dev-tools-grid">
            ${metaRow(t("dashboard_loop"), runtime.executionLoopEnabled ? "ON" : "OFF", `${t("dashboard_interval")} ${formatDurationMs(runtime.executionLoopIntervalMs)}`)}
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
            <div class="actions">
                <button class="button secondary" type="submit">${t("dashboard_apply_runtime")}</button>
            </div>
        </form>
        <p class="muted dev-tools-note">${t("dashboard_runtime_note")}</p>
    ` : `
        <p class="muted dev-tools-note">${t("dashboard_dev_tool_note")}</p>
    `;

    return `
        <div class="panel-header">
            <div>
                <h3>${t("dashboard_dev_tools")}</h3>
                <p class="muted">${t("dashboard_engineering_note")}</p>
            </div>
            <div class="actions">
                <button class="icon-button lab-button" type="button" title="${t("dashboard_dev_test_run")}" aria-label="${t("dashboard_dev_test_run")}" data-action="open-dev-test-run">LAB</button>
                <button class="button secondary" type="button" data-action="run-engine-once">${t("dashboard_run_once")}</button>
            </div>
        </div>
        <div class="action-card dev-tool-card">
            <span class="chip dev-chip">${t("dashboard_dev_tool")}</span>
            ${resultMarkup}
        </div>
    `;
}

export function criticalMetricsPanelMarkup(metrics, pnl) {
    const venueSubmitRows = metrics
        ? Object.entries(metrics.averageSubmitDurationMsByVenue ?? {}).map(([venue, avg]) => {
            const last = metrics.lastSubmitDurationMsByVenue?.[venue] ?? null;
            return metaRow(escapeHtml(venue), formatDurationMs(avg), last != null ? `${t("metrics_last")} ${formatDurationMs(last)}` : "");
        }).join("")
        : "";

    const exchangeLatencySection = venueSubmitRows
        ? `<div class="meta-grid">${venueSubmitRows}</div>`
        : `<p class="muted">${t("empty_engine_no_latency")}</p>`;

    const internalTimingSection = metrics ? `
        <div class="meta-grid">
            ${metaRow(t("metrics_plan_fetch"), formatDurationMs(metrics.averagePlanFetchDurationMs), `${t("metrics_last")} ${formatDurationMs(metrics.lastPlanFetchDurationMs)}`)}
            ${metaRow(t("metrics_attempt_record"), formatDurationMs(metrics.averageAttemptRecordDurationMs), `${t("metrics_last")} ${formatDurationMs(metrics.lastAttemptRecordDurationMs)}`)}
            ${metaRow(t("metrics_execution_run"), formatDurationMs(metrics.averageExecutionRunDurationMs), `${t("metrics_last")} ${formatDurationMs(metrics.lastExecutionRunDurationMs)}`)}
        </div>
    ` : `<p class="muted">${t("empty_engine_no_data")}</p>`;

    const pnlSection = pnl ? `
        <div class="meta-grid">
            ${metaRow(t("metrics_net_pnl"), `${pnl.totalNetPnlUsd >= 0 ? "+" : ""}${formatDecimal(pnl.totalNetPnlUsd, 4)} USD`)}
            ${metaRow(t("metrics_gross_pnl"), `${formatDecimal(pnl.totalGrossPnlUsd, 4)} USD`)}
            ${metaRow(t("metrics_total_fees"), `${formatDecimal(pnl.totalFeesUsd, 4)} USD`)}
            ${metaRow(t("metrics_closed_trades"), formatNumber(pnl.closedTrades), `${formatNumber(pnl.profitableTrades)} ${t("metrics_profitable")}`)}
        </div>
    ` : `<p class="muted">${t("empty_engine_no_closed")}</p>`;

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

export function renderDashboard({ nodes, overview, state, onRunEngineOnce, onUpdateEngineRuntime, onOpenVenue, onOpenDevTestRun }) {
    nodes.globalModeSelect.value = String(overview.globalAccessMode ?? "TESTNET").toUpperCase();
    nodes.dashboardSummary.innerHTML = dashboardSummaryMarkup(overview);
    nodes.dashboardDevTools.innerHTML = dashboardDevToolsMarkup(state.engineRuntime, state.engineRuntimeError);
    nodes.dashboardMetrics.innerHTML = criticalMetricsPanelMarkup(state.engineMetrics, state.pnlAggregate);
    nodes.dashboardVenues.innerHTML = overview.venues.length
        ? overview.venues.map((venue) => venueCard(venue)).join("")
        : emptyState(t("empty_venue_diagnostics"), t("empty_venue_diagnostics_detail"));

    const runOnceButton = nodes.dashboardDevTools.querySelector("[data-action='run-engine-once']");
    if (runOnceButton) {
        runOnceButton.addEventListener("click", onRunEngineOnce);
    }
    const devTestRunButton = nodes.dashboardDevTools.querySelector("[data-action='open-dev-test-run']");
    if (devTestRunButton) {
        devTestRunButton.addEventListener("click", onOpenDevTestRun);
    }
    const runtimeForm = nodes.dashboardDevTools.querySelector("[data-action='update-engine-runtime']");
    if (runtimeForm) {
        runtimeForm.addEventListener("submit", onUpdateEngineRuntime);
    }
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
            executionLoopIntervalMs: numberOrNull(data.get("executionLoopIntervalMs"))
        });
        state.engineRuntimeError = null;
        await refreshCurrentScreen();
        showSuccess(`${t("app_engine_runtime_updated")} ${state.engineRuntime.executionLoopEnabled ? "ON" : "OFF"}, ${t("app_engine_interval")} ${state.engineRuntime.executionLoopIntervalMs} ${t("app_engine_ms")}.`);
    } catch (error) {
        showError(error.message);
    }
}
