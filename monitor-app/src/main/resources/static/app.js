import { api } from "./api.js";
import { createAppState } from "./app/state.js";
import { createNodes } from "./app/dom.js";
import { emptyState, groupAttemptsByTrade, resetDrawer, toIsoOrNull } from "./app/shared.js";
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
    openHistoryTradeDetail
} from "./app/workflows/history-detail.js";
import {
    openVenueDetail
} from "./app/workflows/venue-detail.js";
import {
    openDevTestRunTool
} from "./app/workflows/dev-test-run.js";
import {
    createDrawerActionHandler
} from "./app/workflows/drawer-actions.js";

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

function setLoading(target, label = "Загрузка…") {
    target.innerHTML = emptyState(label, "Подожди, desk обновляет состояние.");
}

function setLoadError(target, message) {
    target.innerHTML = emptyState("Ошибка загрузки", message);
}

const openCandidate = (id) => openCandidateDetail({ id, nodes, showError });
const openEvent = (id) => openEventDetail({ id, nodes, showError });
const openTrade = (id) => openTradeDetail({ id, nodes, showError, onRefresh: refreshCurrentScreen });
const openHistoryTrade = (id) => openHistoryTradeDetail({ id, nodes, showError, onRefresh: refreshCurrentScreen });
const openVenue = (venueName) => openVenueDetail({ venueName, nodes, showError });
const openDevTestRun = () => openDevTestRunTool({ nodes, showError });

async function refreshCurrentScreen() {
    let loadingTarget = null;
    try {
        if (state.screen === "dashboard") {
            setLoading(nodes.dashboardSummary, "Собираю срез контура…");
            setLoading(nodes.dashboardVenues, "Собираю пульс площадок…");
            const [overview, runtimeResult] = await Promise.all([
                api.getOverview(),
                api.getEngineRuntime()
                    .then((runtime) => ({ runtime, error: null }))
                    .catch((error) => ({ runtime: null, error: error.message }))
            ]);
            state.engineRuntime = runtimeResult.runtime;
            state.engineRuntimeError = runtimeResult.error;
            renderDashboard({
                nodes,
                overview,
                state,
                onRunEngineOnce: () => handleRunEngineOnce({ state, refreshCurrentScreen, showSuccess, showError }),
                onUpdateEngineRuntime: (event) => handleUpdateEngineRuntime({ event, state, refreshCurrentScreen, showSuccess, showError }),
                onOpenVenue: openVenue,
                onOpenDevTestRun: openDevTestRun
            });
            return;
        }
        if (state.screen === "candidates") {
            loadingTarget = nodes.candidatesList;
            setLoading(loadingTarget, "Загружаю входящие сигналы…");
            renderCandidates({
                nodes,
                page: await api.listCandidates(state.candidateFilters),
                onOpenCandidate: openCandidate
            });
            return;
        }
        if (state.screen === "events") {
            loadingTarget = nodes.eventsList;
            setLoading(loadingTarget, "Загружаю подтверждённые события…");
            renderFundingEvents({
                nodes,
                page: await api.listFundingEvents(state.eventFilters),
                onOpenEvent: openEvent
            });
            return;
        }
        if (state.screen === "trades") {
            loadingTarget = nodes.tradesList;
            setLoading(loadingTarget, "Загружаю подготовленные сделки…");
            renderTrades({
                nodes,
                trades: await api.listArmedTrades(),
                onOpenTrade: openTrade
            });
            return;
        }
        if (state.screen === "history") {
            loadingTarget = nodes.historyList;
            setLoading(loadingTarget, "Собираю историю сделок…");
            const [trades, attempts] = await Promise.all([
                api.listArmedTrades({ includeHistorical: true }),
                api.listAllOrderAttempts()
            ]);
            renderHistory({
                nodes,
                trades,
                attemptsByTrade: groupAttemptsByTrade(attempts),
                filters: state.historyFilters,
                onOpenHistoryTrade: openHistoryTrade
            });
            return;
        }
        if (state.screen === "venues") {
            loadingTarget = nodes.venuesList;
            setLoading(loadingTarget, "Загружаю диагностику площадок…");
            renderVenues({
                nodes,
                venues: await api.listVenues(),
                onOpenVenue: openVenue
            });
        }
    } catch (error) {
        showError(error.message);
        if (loadingTarget) {
            setLoadError(loadingTarget, error.message);
        }
    }
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
    showSuccess("Контур обновлён.");
});

nodes.operatorTokenInput.value = api.getOperatorToken();
nodes.operatorTokenForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    api.setOperatorToken(nodes.operatorTokenInput.value);
    await Promise.all([refreshCurrentScreen(), loadGlobalMode()]);
    showSuccess("Operator token сохранён в localStorage.");
});

nodes.globalModeForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
        await api.setGlobalVenueMode(nodes.globalModeSelect.value);
        await Promise.all([refreshCurrentScreen(), loadGlobalMode()]);
        showSuccess("Global access mode обновлён.");
    } catch (error) {
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

async function loadGlobalMode() {
    try {
        const globalMode = await api.getGlobalVenueMode();
        nodes.globalModeSelect.value = String(globalMode.mode ?? "TESTNET").toUpperCase();
    } catch (error) {
        showError(error.message);
    }
}

await loadGlobalMode();
await refreshCurrentScreen();
