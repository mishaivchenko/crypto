import { api } from "../../api.js";
import {
    emptyState,
    escapeHtml,
    formatBadge,
    formatDecimal,
    metaRow,
    section
} from "../shared.js";

export async function openDevTestRunTool({ nodes, showError }) {
    nodes.drawerType.textContent = "Dev Tool";
    nodes.drawerTitle.textContent = "Bybit/Gate test run";
    nodes.drawerContent.innerHTML = emptyState("Загружаю dev test run options.", "Нужны active instruments из synced metadata.");
    try {
        const options = await api.getDevTestRunOptions();
        renderDevTestRunStart({ nodes, options });
    } catch (error) {
        showError(error.message);
    }
}

export function renderDevTestRunStart({ nodes, options }) {
    nodes.drawerType.textContent = "Dev Tool";
    nodes.drawerTitle.textContent = "Bybit/Gate test run";
    nodes.drawerContent.innerHTML = buildDevTestRunStart(options);
    wireSymbolPicker(nodes, options);
}

export function renderDevTestRunCreated({ nodes, options, run, execution = null }) {
    nodes.drawerType.textContent = "Dev Tool";
    nodes.drawerTitle.textContent = `${run.venue} ${run.symbol}`;
    nodes.drawerContent.innerHTML = buildDevTestRunCreated({ options, run, execution });
}

function buildDevTestRunStart(options) {
    const venues = options.venues ?? [];
    const hasSymbols = venues.some((venue) => (venue.symbols ?? []).length);
    const mode = String(options.currentMode ?? "TESTNET").toUpperCase();
    const production = mode === "PRODUCTION";
    const disabled = !options.enabled || !hasSymbols;
    const firstVenue = venues[0]?.venue ?? "bybit";
    const firstSymbols = venues[0]?.symbols ?? [];

    return `
        ${section("Mode", `
            <div class="meta-grid">
                ${metaRow("Current mode", formatBadge("venue", mode, production ? "bad" : "good"), production ? "REAL PRODUCTION ORDER requires typed confirmation." : "Testnet order path.")}
                ${metaRow("Max notional", `${formatDecimal(options.engineRuntime?.maxNotionalUsd ?? 25, 2)} USDT`)}
                ${metaRow("Live orders", options.engineRuntime?.liveOrderEnabled ? "ON" : "OFF")}
                ${metaRow("Kill switch", options.engineRuntime?.killSwitchEnabled ? "ON" : "OFF")}
            </div>
            ${safetyIssues(options)}
        `)}
        ${section("Create", disabled ? emptyState("Dev test run недоступен.", "Проверь MONITOR_DEV_TEST_TOOL_ENABLED и synced active instruments.") : `
            <form class="drawer-form" data-action="create-dev-test-run">
                <div class="drawer-form-row labeled-row">
                    <label class="field">
                        <span>Venue</span>
                        <select name="venue" data-dev-test-venue-select>
                            ${venues.map((venue) => `<option value="${escapeHtml(venue.venue)}">${escapeHtml(venue.venue)}</option>`).join("")}
                        </select>
                    </label>
                    <label class="field">
                        <span>Symbol</span>
                        <input name="symbol" list="dev-test-symbols" data-dev-test-symbol-input value="${escapeHtml(firstSymbols[0]?.symbol ?? "")}">
                        <datalist id="dev-test-symbols" data-dev-test-symbol-list>
                            ${symbolOptions(firstSymbols)}
                        </datalist>
                    </label>
                </div>
                <div class="drawer-form-row labeled-row">
                    <label class="field">
                        <span>Notional, USDT</span>
                        <input name="notionalUsd" type="number" min="1" max="25" step="0.01" value="25">
                    </label>
                    <label class="field">
                        <span>Scope</span>
                        <input value="${escapeHtml(firstVenue)} single trade" readonly>
                    </label>
                </div>
                <div class="actions">
                    <button class="button" type="submit">Create</button>
                </div>
            </form>
        `)}
    `;
}

function buildDevTestRunCreated({ options, run, execution }) {
    const mode = String(run.mode ?? options.currentMode ?? "TESTNET").toUpperCase();
    const production = mode === "PRODUCTION";
    const safety = options.safetyIssues ?? [];
    const liveDisabled = production && safety.length > 0;
    const requiredConfirm = `${run.venue} ${run.symbol} LIVE`;

    return `
        ${section("Run", `
            <div class="meta-grid">
                ${metaRow("Armed Trade", `#${escapeHtml(run.armedTradeId)}`, "DEV_TEST")}
                ${metaRow("Mode", formatBadge("venue", mode, production ? "bad" : "good"), production ? "REAL PRODUCTION ORDER" : "testnet execution")}
                ${metaRow("Venue", escapeHtml(run.venue))}
                ${metaRow("Symbol", escapeHtml(run.symbol))}
                ${metaRow("Notional", `${formatDecimal(run.notionalUsd, 2)} USDT`)}
                ${metaRow("State", formatBadge("trade", run.status ?? "ARMED"))}
            </div>
            ${safetyIssues(options)}
        `)}
        ${production ? section("Production confirm", `
            <div class="action-card danger-zone">
                <span class="chip dev-chip">REAL PRODUCTION ORDER</span>
                <p class="helper-text">Typed confirm: <strong>${escapeHtml(requiredConfirm)}</strong></p>
                <input name="productionConfirm" data-production-confirm type="text" autocomplete="off" placeholder="${escapeHtml(requiredConfirm)}">
            </div>
        `) : ""}
        ${section("Actions", `
            <div class="actions">
                <button class="button" type="button" data-action="run-dev-test-entry" data-armed-trade-id="${escapeHtml(run.armedTradeId)}" data-venue="${escapeHtml(run.venue)}" data-symbol="${escapeHtml(run.symbol)}" data-mode="${escapeHtml(mode)}" data-notional-usd="${escapeHtml(run.notionalUsd)}" ${liveDisabled ? "disabled" : ""}>Run entry</button>
                <button class="button secondary" type="button" data-action="run-dev-test-exit" data-armed-trade-id="${escapeHtml(run.armedTradeId)}" data-venue="${escapeHtml(run.venue)}" data-symbol="${escapeHtml(run.symbol)}" data-mode="${escapeHtml(mode)}" data-notional-usd="${escapeHtml(run.notionalUsd)}" ${liveDisabled ? "disabled" : ""}>Run exit</button>
            </div>
            ${execution ? executionResult(execution) : `<p class="helper-text">Entry creates/updates position, exit closes it with reduce-only MARKET.</p>`}
        `)}
    `;
}

function safetyIssues(options) {
    const issues = options.safetyIssues ?? [];
    if (!issues.length) {
        return `<p class="helper-text">Safety gates aligned for current mode.</p>`;
    }
    return `
        <div class="action-note">
            ${issues.map((issue) => `<p class="helper-text">${escapeHtml(issue)}</p>`).join("")}
        </div>
    `;
}

function executionResult(execution) {
    return `
        <div class="action-note">
            <p class="helper-text">${escapeHtml(execution.phase)} submitted ${escapeHtml(execution.execution?.attemptsSubmitted ?? 0)}, skipped ${escapeHtml(execution.execution?.attemptsSkipped ?? 0)}.</p>
        </div>
    `;
}

function wireSymbolPicker(nodes, options) {
    const venueSelect = nodes.drawerContent.querySelector("[data-dev-test-venue-select]");
    const symbolInput = nodes.drawerContent.querySelector("[data-dev-test-symbol-input]");
    const symbolList = nodes.drawerContent.querySelector("[data-dev-test-symbol-list]");
    if (!venueSelect || !symbolInput || !symbolList) {
        return;
    }
    venueSelect.addEventListener("change", () => {
        const venue = (options.venues ?? []).find((item) => item.venue === venueSelect.value);
        const symbols = venue?.symbols ?? [];
        symbolList.innerHTML = symbolOptions(symbols);
        symbolInput.value = symbols[0]?.symbol ?? "";
    });
}

function symbolOptions(symbols) {
    return (symbols ?? [])
        .map((symbol) => `<option value="${escapeHtml(symbol.symbol)}">${escapeHtml(symbol.symbol)} · ${escapeHtml(symbol.venueSymbol)}</option>`)
        .join("");
}
