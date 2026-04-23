export function createNodes(doc = document) {
    return {
        nav: doc.getElementById("nav"),
        refreshAllButton: doc.getElementById("refresh-all-button"),
        operatorTokenForm: doc.getElementById("operator-token-form"),
        operatorTokenInput: doc.getElementById("operator-token-input"),
        globalModeForm: doc.getElementById("global-mode-form"),
        globalModeSelect: doc.getElementById("global-mode-select"),
        globalError: doc.getElementById("global-error"),
        globalSuccess: doc.getElementById("global-success"),
        screens: {
            dashboard: doc.getElementById("screen-dashboard"),
            candidates: doc.getElementById("screen-candidates"),
            events: doc.getElementById("screen-events"),
            trades: doc.getElementById("screen-trades"),
            history: doc.getElementById("screen-history"),
            venues: doc.getElementById("screen-venues")
        },
        dashboardSummary: doc.getElementById("dashboard-summary"),
        dashboardDevTools: doc.getElementById("dashboard-dev-tools"),
        dashboardVenues: doc.getElementById("dashboard-venues"),
        candidatesList: doc.getElementById("candidates-list"),
        eventsList: doc.getElementById("events-list"),
        tradesList: doc.getElementById("trades-list"),
        historyList: doc.getElementById("history-list"),
        historyCount: doc.getElementById("history-count"),
        venuesList: doc.getElementById("venues-list"),
        candidateFilters: doc.getElementById("candidate-filters"),
        eventFilters: doc.getElementById("event-filters"),
        historyFilters: doc.getElementById("history-filters"),
        drawerType: doc.getElementById("drawer-type"),
        drawerTitle: doc.getElementById("drawer-title"),
        drawerContent: doc.getElementById("drawer-content"),
        drawerClose: doc.getElementById("drawer-close")
    };
}
