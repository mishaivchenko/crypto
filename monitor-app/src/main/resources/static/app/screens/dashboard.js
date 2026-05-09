import { api } from "../../api.js";
import {
    emptyState,
    escapeHtml,
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

export function dashboardSummaryMarkup(overview) {
    return `
        ${summaryCard("Signal Queue", overview.pendingCandidates, "Очередь на operator review", "info")}
        ${summaryCard("Funding Events", overview.fundingEvents, `${overview.discoveredEvents} ещё не armed`, "warning")}
        ${summaryCard("Prepared Trades", overview.armedTrades, "Подготовлены для engine", "good")}
        ${summaryCard("Access mode", String(overview.globalAccessMode ?? "testnet").toUpperCase(), `${overview.activeVenues} venues · build ${overview.version}`, "neutral", true)}
    `;
}

export function dashboardDevToolsMarkup(runtime, runtimeError) {
    if (runtimeError) {
        return `
            <div class="panel-header">
            <div>
                <h3>Dev Tools</h3>
                <p class="muted">Runtime control временно недоступен.</p>
            </div>
            <div class="actions">
                <button class="icon-button lab-button" type="button" title="Dev test run" aria-label="Dev test run" data-action="open-dev-test-run">LAB</button>
                <button class="button secondary" type="button" data-action="run-engine-once">Run once · dev</button>
            </div>
        </div>
            <div class="action-card dev-tool-card">
                <span class="chip dev-chip">DEV TOOL</span>
                <p class="helper-text">${escapeHtml(runtimeError)}</p>
                <p class="muted dev-tools-note">Проверь, что engine поднят и monitor видит его по internal base URL.</p>
            </div>
        `;
    }

    const resultMarkup = runtime ? `
        <div class="meta-grid dev-tools-grid">
            ${metaRow("Loop", runtime.executionLoopEnabled ? "ON" : "OFF", `interval ${formatDurationMs(runtime.executionLoopIntervalMs)}`)}
            ${metaRow("Runtime updated", formatInstant(runtime.runtimeUpdatedAt))}
            ${metaRow("Scheduled loop", runtime.lastRunFinishedAt ? formatInstant(runtime.lastRunFinishedAt) : "ещё не запускался", runtime.lastRunFinishedAt ? `${formatRelative(runtime.lastRunFinishedAt)} · ${formatDurationMs(runtime.lastExecutionRunDurationMs)}` : "")}
            ${metaRow("Loop result", `${formatNumber(runtime.lastAttemptsSubmitted)} submitted / ${formatNumber(runtime.lastAttemptsSkipped)} skipped`, `${formatNumber(runtime.lastPlansScanned)} plans scanned`)}
            ${metaRow("Last force-run", runtime.lastForcedRunFinishedAt ? formatInstant(runtime.lastForcedRunFinishedAt) : "ещё не запускался", runtime.lastForcedRunFinishedAt ? `${formatRelative(runtime.lastForcedRunFinishedAt)} · ${formatDurationMs(runtime.lastForcedRunDurationMs)}` : "Нажми Run once · dev, чтобы записать ручной цикл")}
            ${metaRow("Force-run result", `${formatNumber(runtime.lastForcedAttemptsSubmitted)} submitted / ${formatNumber(runtime.lastForcedAttemptsSkipped)} skipped`, `${formatNumber(runtime.lastForcedPlansScanned)} plans scanned`)}
        </div>
        <form class="drawer-form" data-action="update-engine-runtime">
            <div class="drawer-form-row labeled-row">
                <label class="field">
                    <span>Execution loop</span>
                    <select name="executionLoopEnabled">
                        <option value="true" ${runtime.executionLoopEnabled ? "selected" : ""}>Enabled</option>
                        <option value="false" ${!runtime.executionLoopEnabled ? "selected" : ""}>Disabled</option>
                    </select>
                </label>
                <label class="field">
                    <span>Interval, ms</span>
                    <input name="executionLoopIntervalMs" type="number" min="${escapeHtml(runtime.minimumExecutionLoopIntervalMs)}" step="50" value="${escapeHtml(runtime.executionLoopIntervalMs)}">
                </label>
            </div>
            <div class="actions">
                <button class="button secondary" type="submit">Apply runtime</button>
            </div>
        </form>
        <p class="muted dev-tools-note">Runtime changes применяются сразу, но живут до рестарта engine. Базовые defaults всё ещё задаются через compose/env.</p>
    ` : `
        <p class="muted dev-tools-note">Dev tool запускает один принудительный проход engine по waiting/entry-window/overdue планам и сразу пишет Order Attempts в monitor.</p>
    `;

    return `
        <div class="panel-header">
            <div>
                <h3>Dev Tools</h3>
                <p class="muted">Инженерный контур для ручной проверки engine.</p>
            </div>
            <div class="actions">
                <button class="icon-button lab-button" type="button" title="Dev test run" aria-label="Dev test run" data-action="open-dev-test-run">LAB</button>
                <button class="button secondary" type="button" data-action="run-engine-once">Run once · dev</button>
            </div>
        </div>
        <div class="action-card dev-tool-card">
            <span class="chip dev-chip">DEV TOOL</span>
            ${resultMarkup}
        </div>
    `;
}

export function renderDashboard({ nodes, overview, state, onRunEngineOnce, onUpdateEngineRuntime, onOpenVenue, onOpenDevTestRun }) {
    nodes.globalModeSelect.value = String(overview.globalAccessMode ?? "TESTNET").toUpperCase();
    nodes.dashboardSummary.innerHTML = dashboardSummaryMarkup(overview);
    nodes.dashboardDevTools.innerHTML = dashboardDevToolsMarkup(state.engineRuntime, state.engineRuntimeError);
    nodes.dashboardVenues.innerHTML = overview.venues.length
        ? overview.venues.map((venue) => venueCard(venue)).join("")
        : emptyState("Venue diagnostics пока пуст.", "Сделай sync, чтобы подтянуть instrument metadata.");

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
        showSuccess(`Engine run-once completed: scanned ${result.plansScanned}, submitted ${result.attemptsSubmitted}, skipped ${result.attemptsSkipped}.`);
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
        showSuccess(`Engine runtime updated: loop ${state.engineRuntime.executionLoopEnabled ? "ON" : "OFF"}, interval ${state.engineRuntime.executionLoopIntervalMs} ms.`);
    } catch (error) {
        showError(error.message);
    }
}
