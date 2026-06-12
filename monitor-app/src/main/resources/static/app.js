import { api } from "./api.js";
import { createAppState } from "./app/state.js";
import { createNodes } from "./app/dom.js";
import { closeModal, emptyState, groupAttemptsByTrade, resetDrawer, toIsoOrNull } from "./app/shared.js";
import {
    handleRunEngineOnce,
    handleUpdateEngineRuntime,
    renderDashboard
} from "./app/screens/dashboard.js";
import { renderCandidates } from "./app/screens/candidates.js";
import { renderFundingEvents } from "./app/screens/events.js";
import { renderTrades } from "./app/screens/trades.js";
import { renderHistory } from "./app/screens/history.js";
import { renderVenues } from "./app/screens/venues.js";
import { renderSettings } from "./app/screens/settings.js";
import {
    openCandidateDetail
} from "./app/workflows/candidate-detail.js";
import {
    openEventDetail
} from "./app/workflows/event-detail.js";
import {
    openTradeDetail
} from "./app/workflows/trade-detail.js";
import {
    openVenueDetail
} from "./app/workflows/venue-detail.js";
import {
    openDevTestRunTool
} from "./app/workflows/dev-test-run.js";
import {
    createDrawerActionHandler
} from "./app/workflows/drawer-actions.js";
import { getLang, setLang, t } from "./i18n.js";

const state = createAppState();
const nodes = createNodes(document);

function showBanner(target, message) {
    target.textContent = message;
    target.classList.remove("hidden");
    window.clearTimeout(target._timeoutId);
    target._timeoutId = window.setTimeout(() => target.classList.add("hidden"), 4200);
}

function showError(message) {
    showBanner(nodes.globalError, message);
}

function showSuccess(message) {
    showBanner(nodes.globalSuccess, message);
}

function setLoading(target, label) {
    const loadingLabel = label ?? t("loading_default");
    target.innerHTML = emptyState(loadingLabel, t("loading_detail"));
}

function setLoadError(target, message) {
    target.innerHTML = emptyState(t("loading_error_title"), message);
}

const openCandidate = (id) => openCandidateDetail({ id, nodes, showError });
const openEvent = (id) => openEventDetail({ id, nodes, showError });
const openTrade = (id) => openTradeDetail({ id, nodes, showError, onRefresh: refreshCurrentScreen });
const openVenue = (venueName) => openVenueDetail({ venueName, nodes, showError });
const openDevTestRun = () => openDevTestRunTool({ nodes, showError });

async function refreshCurrentScreen() {
    let loadingTarget = null;
    try {
        if (state.screen === "dashboard") {
            setLoading(nodes.dashboardSummary, t("loading_dashboard"));
            setLoading(nodes.dashboardVenues, t("loading_venues_pulse"));
            const [overview, runtimeResult, pnlAggregate] = await Promise.all([
                api.getOverview(),
                api.getEngineRuntime()
                    .then((runtime) => ({ runtime, error: null }))
                    .catch((error) => ({ runtime: null, error: error.message })),
                api.getPnlAggregate()
            ]);
            state.engineRuntime = runtimeResult.runtime;
            state.engineRuntimeError = runtimeResult.error;
            state.pnlAggregate = pnlAggregate;
            // Load waterfall per venue
            const venueNames = (overview.venues ?? []).map((v) => v.venue);
            const waterfallResults = await Promise.all(
                venueNames.map((v) => api.getOrderWaterfall(v).catch(() => null))
            );
            state.waterfallByVenue = Object.fromEntries(
                venueNames.map((v, i) => [v, waterfallResults[i]])
            );
            renderDashboard({
                nodes,
                overview,
                state,
                onOpenVenue: openVenue,
                onNavigate: switchScreen
            });
            return;
        }
        if (state.screen === "candidates") {
            loadingTarget = nodes.candidatesList;
            setLoading(loadingTarget, t("loading_candidates"));
            const page = await api.listCandidates(state.candidateFilters);
            state.lastCandidates = page?.content ?? [];
            await renderCandidates({ nodes, page, showError, onRefresh: refreshCurrentScreen });
            return;
        }
        if (state.screen === "events") {
            loadingTarget = nodes.eventsList;
            setLoading(loadingTarget, t("loading_events"));
            renderFundingEvents({
                nodes,
                page: await api.listFundingEvents(state.eventFilters),
                showError,
                onRefresh: refreshCurrentScreen
            });
            return;
        }
        if (state.screen === "trades") {
            loadingTarget = nodes.tradesList;
            setLoading(loadingTarget, t("loading_trades"));
            const trades = await api.listArmedTrades();
            state.lastTrades = trades;
            renderTrades({
                nodes,
                trades,
                showError,
                onRefresh: refreshCurrentScreen
            });
            return;
        }
        if (state.screen === "history") {
            loadingTarget = nodes.historyList;
            setLoading(loadingTarget, t("loading_history"));
            const [trades, attempts] = await Promise.all([
                api.listArmedTrades({ includeHistorical: true }),
                api.listAllOrderAttempts()
            ]);
            const tradeIds = trades.map((tr) => tr.id);
            const outcomesByTrade = await api.getOutcomesByTradeIds(tradeIds);
            renderHistory({
                nodes,
                trades,
                attemptsByTrade: groupAttemptsByTrade(attempts),
                outcomesByTrade,
                filters: state.historyFilters,
                showError,
                onRefresh: refreshCurrentScreen
            });
            return;
        }
        if (state.screen === "venues") {
            loadingTarget = nodes.venuesList;
            setLoading(loadingTarget, t("loading_venues"));
            const [venues, overview, allTimings] = await Promise.all([
                api.listVenues(),
                api.getOverview().catch(() => null),
                api.listVenueTimings(null).catch(() => [])
            ]);
            const overviewByVenue = Object.fromEntries(
                (overview?.venues ?? []).map(v => [v.venue, v])
            );
            // p50 from order-submit timing row per venue
            const p50ByVenue = {};
            for (const t of (allTimings ?? [])) {
                if (t.operation === "order-submit" && t.venue && t.p50DurationMs != null) {
                    p50ByVenue[t.venue] = t.p50DurationMs;
                }
            }
            const enrichedVenues = venues.map(v => ({
                ...v,
                enrichmentCoveragePct: overviewByVenue[v.venue]?.enrichmentCoveragePct ?? null,
                p50DurationMs: p50ByVenue[v.venue] ?? null
            }));
            renderVenues({
                nodes,
                venues: enrichedVenues,
                onOpenVenue: openVenue
            });
        }
        if (state.screen === "settings") {
            const runtimeResult = await api.getEngineRuntime()
                .then((runtime) => ({ runtime, error: null }))
                .catch((error) => ({ runtime: null, error: error.message }));
            state.engineRuntime = runtimeResult.runtime;
            state.engineRuntimeError = runtimeResult.error;
            renderSettings({
                nodes,
                state,
                showError,
                showSuccess,
                onRunEngineOnce: () => handleRunEngineOnce({ state, refreshCurrentScreen, showSuccess, showError }),
                onUpdateEngineRuntime: (event) => handleUpdateEngineRuntime({ event, state, refreshCurrentScreen, showSuccess, showError }),
                onOpenDevTestRun: openDevTestRun,
                switchLang,
                loadGlobalMode
            });
        }
    } catch (error) {
        showError(error.message);
        if (loadingTarget) {
            setLoadError(loadingTarget, error.message);
        }
    }
}

function applyStaticTranslations() {
    const nav = nodes.nav;
    nav.querySelectorAll(".nav-link[data-screen]").forEach((btn) => {
        const screenKey = `nav_${btn.dataset.screen.replace("-", "_")}`;
        const mapped = {
            dashboard: "nav_overview",
            candidates: "nav_signal_queue",
            events: "nav_funding_events",
            trades: "nav_prepared_trades",
            history: "nav_trade_history",
            venues: "nav_venue_access",
            settings: "nav_settings"
        };
        const key = mapped[btn.dataset.screen] ?? screenKey;
        btn.textContent = t(key);
    });

    const operatorTokenLabel = document.querySelector("#operator-token-form label span");
    if (operatorTokenLabel) operatorTokenLabel.textContent = t("topbar_operator_token");
    nodes.operatorTokenInput.placeholder = t("topbar_token_placeholder");
    const saveTokenBtn = document.querySelector("#operator-token-form button[type='submit']");
    if (saveTokenBtn) saveTokenBtn.textContent = t("topbar_save_token");

    const accessModeLabel = document.querySelector("#global-mode-form label span");
    if (accessModeLabel) accessModeLabel.textContent = t("topbar_access_mode");
    const applyModeBtn = document.querySelector("#global-mode-form button[type='submit']");
    if (applyModeBtn) applyModeBtn.textContent = t("topbar_apply_mode");
    nodes.refreshAllButton.textContent = t("topbar_refresh");
    const settingsToggleBtn = document.getElementById("settings-toggle");
    if (settingsToggleBtn) settingsToggleBtn.setAttribute("aria-label", t("topbar_settings_label"));
    if (nodes.aiToggleLabel) nodes.aiToggleLabel.textContent = t("ai_toggle_label");
    const hft = document.getElementById("history-filter-toggle");
    if (hft) {
        const isCollapsed = document.getElementById("history-filter-rail")?.classList.contains("is-collapsed");
        hft.textContent = t(isCollapsed ? "history_filters_show" : "history_filters_hide");
    }

    const testnetOption = nodes.globalModeSelect.querySelector("option[value='TESTNET']");
    if (testnetOption) testnetOption.textContent = t("topbar_testnet");
    const productionOption = nodes.globalModeSelect.querySelector("option[value='PRODUCTION']");
    if (productionOption) productionOption.textContent = t("topbar_production");

    const drawerType = document.getElementById("drawer-type");
    if (drawerType) drawerType.textContent = t("inspector_type");
    const drawerTitle = document.getElementById("drawer-title");
    if (drawerTitle && drawerTitle.textContent.trim() === nodes.drawerTitle?.textContent?.trim()) {
        drawerTitle.textContent = t("inspector_title_default");
    }
    const drawerPlaceholder = document.querySelector("#detail-drawer .drawer-body p.muted");
    if (drawerPlaceholder) drawerPlaceholder.textContent = t("inspector_placeholder");

    const venuePulseHeader = document.querySelector("#screen-dashboard .panel-header h3");
    if (venuePulseHeader) {
        venuePulseHeader.textContent = t("venue_pulse");
    }

    const overviewEyebrow = document.querySelector("#screen-dashboard .section-header .eyebrow");
    if (overviewEyebrow) overviewEyebrow.textContent = t("nav_overview");
    const overviewH2 = document.querySelector("#screen-dashboard .section-header h2");
    if (overviewH2) overviewH2.textContent = t("section_overview_h2");

    const candidatesEyebrow = document.querySelector("#screen-candidates .section-header .eyebrow");
    if (candidatesEyebrow) candidatesEyebrow.textContent = t("nav_signal_queue");
    const candidatesH2 = document.querySelector("#screen-candidates .section-header h2");
    if (candidatesH2) candidatesH2.textContent = t("section_candidates_h2");

    const eventsEyebrow = document.querySelector("#screen-events .section-header .eyebrow");
    if (eventsEyebrow) eventsEyebrow.textContent = t("nav_funding_events");
    const eventsH2 = document.querySelector("#screen-events .section-header h2");
    if (eventsH2) eventsH2.textContent = t("section_events_h2");

    const tradesEyebrow = document.querySelector("#screen-trades .section-header .eyebrow");
    if (tradesEyebrow) tradesEyebrow.textContent = t("nav_prepared_trades");
    const tradesH2 = document.querySelector("#screen-trades .section-header h2");
    if (tradesH2) tradesH2.textContent = t("section_trades_h2");

    const historyEyebrow = document.querySelector("#screen-history .section-header .eyebrow");
    if (historyEyebrow) historyEyebrow.textContent = t("nav_trade_history");
    const historyH2 = document.querySelector("#screen-history .section-header h2");
    if (historyH2) historyH2.textContent = t("section_history_h2");

    const venuesEyebrow = document.querySelector("#screen-venues .section-header .eyebrow");
    if (venuesEyebrow) venuesEyebrow.textContent = t("nav_venue_access");
    const venuesH2 = document.querySelector("#screen-venues .section-header h2");
    if (venuesH2) venuesH2.textContent = t("section_venues_h2");

    const candidateFiltersForm = document.getElementById("candidate-filters");
    if (candidateFiltersForm) {
        const symbolInput = candidateFiltersForm.querySelector("input[name='symbol']");
        if (symbolInput) symbolInput.placeholder = t("filter_symbol");
        const venueInput = candidateFiltersForm.querySelector("input[name='venue']");
        if (venueInput) venueInput.placeholder = t("filter_venue");
        const detectedInput = candidateFiltersForm.querySelector("input[name='detectedFrom']");
        if (detectedInput) detectedInput.placeholder = t("filter_detected_from");
        const statusSelect = candidateFiltersForm.querySelector("select[name='status']");
        if (statusSelect) {
            statusSelect.options[0].textContent = t("filter_all_statuses");
            statusSelect.options[1].textContent = t("status_candidate_NEW");
            statusSelect.options[2].textContent = t("status_candidate_NORMALIZED");
            statusSelect.options[3].textContent = t("status_candidate_FAILED");
            statusSelect.options[4].textContent = t("status_candidate_REJECTED");
            statusSelect.options[5].textContent = t("status_candidate_EVENT_CREATED");
            statusSelect.options[6].textContent = t("status_candidate_DELETED");
        }
        const applyBtn = candidateFiltersForm.querySelector("button[type='submit']");
        if (applyBtn) applyBtn.textContent = t("filter_apply");
    }

    const eventFiltersForm = document.getElementById("event-filters");
    if (eventFiltersForm) {
        const symbolInput = eventFiltersForm.querySelector("input[name='symbol']");
        if (symbolInput) symbolInput.placeholder = t("filter_symbol");
        const venueInput = eventFiltersForm.querySelector("input[name='venue']");
        if (venueInput) venueInput.placeholder = t("filter_venue");
        const statusSelect = eventFiltersForm.querySelector("select[name='status']");
        if (statusSelect) {
            statusSelect.options[0].textContent = t("filter_active_statuses");
            statusSelect.options[1].textContent = t("status_event_DISCOVERED");
            statusSelect.options[2].textContent = t("status_event_ARMED");
            statusSelect.options[3].textContent = t("status_event_EXPIRED");
            statusSelect.options[4].textContent = t("status_event_CANCELLED");
        }
        const applyBtn = eventFiltersForm.querySelector("button[type='submit']");
        if (applyBtn) applyBtn.textContent = t("filter_apply");
    }

    const historyFiltersForm = document.getElementById("history-filters");
    if (historyFiltersForm) {
        const symbolLabel = historyFiltersForm.querySelector("label:nth-of-type(1) span");
        if (symbolLabel) symbolLabel.textContent = t("filter_symbol");
        const venueLabel = historyFiltersForm.querySelector("label:nth-of-type(2) span");
        if (venueLabel) venueLabel.textContent = t("filter_venue");
        const stateLabel = historyFiltersForm.querySelector("label:has(select[name='state']) span");
        if (stateLabel) stateLabel.textContent = t("filter_state");
        const stateSelect = historyFiltersForm.querySelector("select[name='state']");
        if (stateSelect) {
            stateSelect.options[0].textContent = t("filter_all_states");
            stateSelect.options[1].textContent = t("status_historyStage_PREPARED");
            stateSelect.options[2].textContent = t("status_historyStage_ENTRY_PENDING");
            stateSelect.options[3].textContent = t("status_historyStage_ENTRY_ATTEMPTED");
            stateSelect.options[4].textContent = t("status_historyStage_ATTEMPTS_FAILED");
            stateSelect.options[5].textContent = t("status_historyStage_MISSED_WINDOW");
            stateSelect.options[6].textContent = t("status_historyStage_OPEN");
            stateSelect.options[7].textContent = t("status_historyStage_EXIT_PENDING");
            stateSelect.options[8].textContent = t("status_historyStage_CLOSED");
            stateSelect.options[9].textContent = t("status_historyStage_CANCELLED");
            stateSelect.options[10].textContent = t("status_historyStage_FAILED");
        }
        const healthLabel = historyFiltersForm.querySelector("label:has(select[name='health']) span");
        if (healthLabel) healthLabel.textContent = t("filter_health");
        const healthSelect = historyFiltersForm.querySelector("select[name='health']");
        if (healthSelect) {
            healthSelect.options[0].textContent = t("filter_all_health");
        }
        const fundingFromLabel = historyFiltersForm.querySelector("label:has(input[name='dateFrom']) span");
        if (fundingFromLabel) fundingFromLabel.textContent = t("filter_funding_from");
        const fundingToLabel = historyFiltersForm.querySelector("label:has(input[name='dateTo']) span");
        if (fundingToLabel) fundingToLabel.textContent = t("filter_funding_to");
        const onlyFailedLabel = historyFiltersForm.querySelector("label:has(input[name='onlyFailed']) span");
        if (onlyFailedLabel) onlyFailedLabel.textContent = t("filter_only_failed");
        const onlyManualLabel = historyFiltersForm.querySelector("label:has(input[name='onlyManual']) span");
        if (onlyManualLabel) onlyManualLabel.textContent = t("filter_only_manual");
        const applyBtn = historyFiltersForm.querySelector("button[type='submit']");
        if (applyBtn) applyBtn.textContent = t("filter_apply_filters");
    }
}

function updateLangToggle() {
    const lang = getLang();
    const btn = document.getElementById("lang-toggle");
    if (btn) {
        btn.textContent = lang === "ru" ? "EN" : "RU";
        btn.setAttribute("aria-label", lang === "ru" ? "Switch to English" : "Переключить на русский");
    }
    document.documentElement.lang = lang;
}

function switchLang() {
    const next = getLang() === "ru" ? "en" : "ru";
    setLang(next);
    updateLangToggle();
    applyStaticTranslations();
    refreshCurrentScreen();
}

function switchScreen(screen) {
    state.screen = screen;
    Object.entries(nodes.screens).forEach(([key, element]) => {
        element.classList.toggle("is-visible", key === screen);
    });
    nodes.nav.querySelectorAll(".nav-link").forEach((button) => {
        button.classList.toggle("is-active", button.dataset.screen === screen);
    });
    refreshCurrentScreen();
}

nodes.nav.addEventListener("click", (event) => {
    const button = event.target.closest(".nav-link");
    if (!button) {
        return;
    }
    switchScreen(button.dataset.screen);
});

nodes.refreshAllButton.addEventListener("click", async () => {
    await Promise.all([refreshCurrentScreen(), loadGlobalMode()]);
    showSuccess(t("app_refreshed"));
});

nodes.operatorTokenInput.value = api.getOperatorToken();
nodes.operatorTokenForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    api.setOperatorToken(nodes.operatorTokenInput.value);
    await Promise.all([refreshCurrentScreen(), loadGlobalMode()]);
    showSuccess(t("app_token_saved"));
});

nodes.globalModeForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
        await api.setGlobalVenueMode(nodes.globalModeSelect.value);
        await Promise.all([refreshCurrentScreen(), loadGlobalMode()]);
        showSuccess(t("app_mode_updated"));
    } catch (error) {
        showError(error.message);
    }
});

nodes.aiToggleCheckbox.addEventListener("change", async () => {
    try {
        await api.setAiEnabled(nodes.aiToggleCheckbox.checked);
        showSuccess(nodes.aiToggleCheckbox.checked ? t("ai_toggle_enabled") : t("ai_toggle_disabled"));
    } catch (error) {
        nodes.aiToggleCheckbox.checked = !nodes.aiToggleCheckbox.checked;
        showError(error.message);
    }
});

nodes.candidateFilters.addEventListener("submit", async (event) => {
    event.preventDefault();
    const entries = Object.fromEntries(new FormData(event.currentTarget).entries());
    state.candidateFilters = {
        ...entries,
        detectedFrom: toIsoOrNull(entries.detectedFrom)
    };
    await refreshCurrentScreen();
});

nodes.eventFilters.addEventListener("submit", async (event) => {
    event.preventDefault();
    state.eventFilters = Object.fromEntries(new FormData(event.currentTarget).entries());
    await refreshCurrentScreen();
});

nodes.historyFilters.addEventListener("submit", async (event) => {
    event.preventDefault();
    const entries = Object.fromEntries(new FormData(event.currentTarget).entries());
    state.historyFilters = {
        ...entries,
        dateFrom: toIsoOrNull(entries.dateFrom),
        dateTo: toIsoOrNull(entries.dateTo),
        onlyFailed: Boolean(entries.onlyFailed),
        onlyManual: Boolean(entries.onlyManual)
    };
    await refreshCurrentScreen();
});

nodes.drawerClose.addEventListener("click", () => {
    resetDrawer(nodes);
});

nodes.modalClose.addEventListener("click", () => closeModal(nodes));

nodes.inspectorModal.addEventListener("click", (e) => {
    if (e.target === nodes.inspectorModal || e.target.classList.contains("inspector-backdrop")) {
        closeModal(nodes);
    }
});

document.addEventListener("keydown", (e) => {
    if (e.key === "Escape" && !nodes.inspectorModal.hidden) closeModal(nodes);
});

const langToggle = document.getElementById("lang-toggle");
if (langToggle) {
    langToggle.addEventListener("click", switchLang);
}

function openSettings() {
    document.getElementById("settings-panel").hidden = false;
    document.getElementById("settings-toggle").setAttribute("aria-expanded", "true");
    loadAiPerformance();
}

function closeSettings() {
    const panel = document.getElementById("settings-panel");
    if (panel) panel.hidden = true;
    const toggle = document.getElementById("settings-toggle");
    if (toggle) toggle.setAttribute("aria-expanded", "false");
}

const settingsToggle = document.getElementById("settings-toggle");
if (settingsToggle) {
    settingsToggle.addEventListener("click", (e) => {
        e.stopPropagation();
        const panel = document.getElementById("settings-panel");
        if (panel.hidden) {
            openSettings();
        } else {
            closeSettings();
        }
    });
    document.addEventListener("click", (e) => {
        const panel = document.getElementById("settings-panel");
        if (panel && !panel.hidden && !panel.contains(e.target) && e.target !== settingsToggle) {
            closeSettings();
        }
    });
}

const historyFilterToggle = document.getElementById("history-filter-toggle");
const historyFilterRail = document.getElementById("history-filter-rail");
if (historyFilterToggle && historyFilterRail) {
    const historyBoard = historyFilterRail.closest(".history-board");
    historyFilterToggle.addEventListener("click", () => {
        const isCollapsed = historyFilterRail.classList.toggle("is-collapsed");
        historyBoard?.classList.toggle("rail-collapsed", isCollapsed);
        historyFilterToggle.setAttribute("aria-expanded", String(!isCollapsed));
        historyFilterToggle.textContent = t(isCollapsed ? "history_filters_show" : "history_filters_hide");
    });
}

const handleDrawerAction = createDrawerActionHandler({
    nodes,
    refreshCurrentScreen,
    showSuccess,
    showError,
    switchScreen,
    openCandidateDetail: openCandidate,
    openEventDetail: openEvent,
    openTradeDetail: openTrade,
    openVenueDetail: openVenue
});

nodes.drawerContent.addEventListener("submit", handleDrawerAction);
nodes.drawerContent.addEventListener("click", handleDrawerAction);
nodes.modalContent.addEventListener("submit", handleDrawerAction);
nodes.modalContent.addEventListener("click", handleDrawerAction);

async function loadGlobalMode() {
    try {
        const globalMode = await api.getGlobalVenueMode();
        nodes.globalModeSelect.value = String(globalMode.mode ?? "TESTNET").toUpperCase();
    } catch (error) {
        showError(error.message);
    }
}

async function loadAiStatus() {
    try {
        const status = await api.getAiStatus();
        nodes.aiToggleCheckbox.checked = Boolean(status.enabled);
    } catch {
        // non-critical — leave checkbox as-is
    }
}

async function loadAiPerformance() {
    const container = document.getElementById("ai-performance-container");
    if (!container) return;
    try {
        const perf = await api.getAiPerformance();
        if (!perf || perf.totalTrades < 3) {
            container.innerHTML = `<p class="helper-text muted">${t("ai_performance_no_data")}</p>`;
            return;
        }
        const rows = perf.stats.map(s => {
            const winRate = s.winRate != null ? `${Math.round(s.winRate * 100)}%` : "—";
            const avgPnl = s.avgPnlUsd != null
                ? `${parseFloat(s.avgPnlUsd) >= 0 ? "+" : ""}$${parseFloat(s.avgPnlUsd).toFixed(2)}`
                : "—";
            const rec = t(`ai_recommendation_${s.recommendation}`) || s.recommendation;
            return `<tr><td>${rec}</td><td>${s.tradeCount}</td><td>${winRate}</td><td>${avgPnl}</td></tr>`;
        }).join("");
        container.innerHTML = `
            <table class="perf-table">
                <thead><tr>
                    <th></th>
                    <th>${t("ai_performance_trades")}</th>
                    <th>${t("ai_performance_win_rate")}</th>
                    <th>${t("ai_performance_avg_pnl")}</th>
                </tr></thead>
                <tbody>${rows}</tbody>
            </table>`;
    } catch (err) {
        console.debug("AI performance load failed:", err);
    }
}

applyStaticTranslations();
updateLangToggle();
await Promise.all([loadGlobalMode(), loadAiStatus()]);
await refreshCurrentScreen();
