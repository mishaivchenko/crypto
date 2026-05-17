import { api } from "../../api.js";
import {
    emptyState,
    escapeHtml,
    formatBadge,
    formatDecimal,
    metaRow,
    openModal,
    section
} from "../shared.js";
import { t } from "../../i18n.js";

export async function openDevTestRunTool({ nodes, showError }) {
    nodes.modalType.textContent = t("dev_tool_type");
    nodes.modalTitle.textContent = t("dev_tool_title");
    nodes.modalContent.innerHTML = emptyState(t("dev_loading"), t("dev_loading_detail"));
    openModal(nodes);
    try {
        const options = await api.getDevTestRunOptions();
        renderDevTestRunStart({ nodes, options });
    } catch (error) {
        showError(error.message);
    }
}

export function renderDevTestRunStart({ nodes, options }) {
    nodes.modalType.textContent = t("dev_tool_type");
    nodes.modalTitle.textContent = t("dev_tool_title");
    nodes.modalContent.innerHTML = buildDevTestRunStart(options);
    wireSymbolPicker(nodes, options);
}

export function renderDevTestRunCreated({ nodes, options, run, execution = null }) {
    nodes.modalType.textContent = t("dev_tool_type");
    nodes.modalTitle.textContent = `${run.venue} ${run.symbol}`;
    nodes.modalContent.innerHTML = buildDevTestRunCreated({ options, run, execution });
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
        ${section(t("dev_mode"), `
            <div class="meta-grid">
                ${metaRow(t("dev_current_mode"), formatBadge("venue", mode, production ? "bad" : "good"), production ? t("dev_production_confirm_required") : t("dev_testnet_order_path"))}
                ${metaRow(t("dev_max_notional"), `${formatDecimal(options.engineRuntime?.maxNotionalUsd ?? 25, 2)} USDT`)}
                ${metaRow(t("dev_live_orders"), options.engineRuntime?.liveOrderEnabled ? "ON" : "OFF")}
                ${metaRow(t("dev_kill_switch"), options.engineRuntime?.killSwitchEnabled ? "ON" : "OFF")}
            </div>
            ${safetyIssues(options)}
        `)}
        ${section(t("dev_create"), disabled ? emptyState(t("dev_unavailable"), t("dev_unavailable_detail")) : `
            <form class="drawer-form" data-action="create-dev-test-run">
                <div class="drawer-form-row labeled-row">
                    <label class="field">
                        <span>${t("dev_venue")}</span>
                        <select name="venue" data-dev-test-venue-select>
                            ${venues.map((venue) => `<option value="${escapeHtml(venue.venue)}">${escapeHtml(venue.venue)}</option>`).join("")}
                        </select>
                    </label>
                    <label class="field">
                        <span>${t("dev_symbol")}</span>
                        <input name="symbol" list="dev-test-symbols" data-dev-test-symbol-input value="${escapeHtml(firstSymbols[0]?.symbol ?? "")}">
                        <datalist id="dev-test-symbols" data-dev-test-symbol-list>
                            ${symbolOptions(firstSymbols)}
                        </datalist>
                    </label>
                </div>
                <div class="drawer-form-row labeled-row">
                    <label class="field">
                        <span>${t("dev_notional")}</span>
                        <input name="notionalUsd" type="number" min="1" max="25" step="0.01" value="25">
                    </label>
                    <label class="field">
                        <span>${t("dev_scope")}</span>
                        <input value="${escapeHtml(firstVenue)} single trade" readonly>
                    </label>
                </div>
                <div class="actions">
                    <button class="button" type="submit">${t("dev_create_button")}</button>
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
        ${section(t("dev_run"), `
            <div class="meta-grid">
                ${metaRow(t("dev_armed_trade"), `#${escapeHtml(run.armedTradeId)}`, t("dev_dev_test"))}
                ${metaRow(t("dev_mode"), formatBadge("venue", mode, production ? "bad" : "good"), production ? t("dev_real_order") : "testnet execution")}
                ${metaRow(t("dev_venue_label"), escapeHtml(run.venue))}
                ${metaRow(t("dev_symbol"), escapeHtml(run.symbol))}
                ${metaRow(t("dev_notional"), `${formatDecimal(run.notionalUsd, 2)} USDT`)}
                ${metaRow(t("dev_state"), formatBadge("trade", run.status ?? "ARMED"))}
            </div>
            ${safetyIssues(options)}
        `)}
        ${production ? section(t("dev_production_confirm"), `
            <div class="action-card danger-zone">
                <span class="chip dev-chip">${t("dev_real_order")}</span>
                <p class="helper-text">${t("dev_typed_confirm")} <strong>${escapeHtml(requiredConfirm)}</strong></p>
                <input name="productionConfirm" data-production-confirm type="text" autocomplete="off" placeholder="${escapeHtml(requiredConfirm)}">
            </div>
        `) : ""}
        ${section(t("dev_actions"), `
            <div class="actions">
                <button class="button" type="button" data-action="run-dev-test-entry" data-armed-trade-id="${escapeHtml(run.armedTradeId)}" data-venue="${escapeHtml(run.venue)}" data-symbol="${escapeHtml(run.symbol)}" data-mode="${escapeHtml(mode)}" data-notional-usd="${escapeHtml(run.notionalUsd)}" ${liveDisabled ? "disabled" : ""}>${t("dev_run_entry")}</button>
                <button class="button secondary" type="button" data-action="run-dev-test-exit" data-armed-trade-id="${escapeHtml(run.armedTradeId)}" data-venue="${escapeHtml(run.venue)}" data-symbol="${escapeHtml(run.symbol)}" data-mode="${escapeHtml(mode)}" data-notional-usd="${escapeHtml(run.notionalUsd)}" ${liveDisabled ? "disabled" : ""}>${t("dev_run_exit")}</button>
            </div>
            ${execution ? executionResult(execution) : `<p class="helper-text">${t("dev_entry_note")}</p>`}
        `)}
    `;
}

function safetyIssues(options) {
    const issues = options.safetyIssues ?? [];
    if (!issues.length) {
        return `<p class="helper-text">${t("dev_safety_aligned")}</p>`;
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
            <p class="helper-text">${escapeHtml(execution.phase)} ${t("dev_submitted")} ${escapeHtml(execution.execution?.attemptsSubmitted ?? 0)}, ${t("dev_skipped")} ${escapeHtml(execution.execution?.attemptsSkipped ?? 0)}.</p>
        </div>
    `;
}

function wireSymbolPicker(nodes, options) {
    const venueSelect = nodes.modalContent.querySelector("[data-dev-test-venue-select]");
    const symbolInput = nodes.modalContent.querySelector("[data-dev-test-symbol-input]");
    const symbolList = nodes.modalContent.querySelector("[data-dev-test-symbol-list]");
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
