export function createAppState() {
    return {
        screen: "dashboard",
        candidateFilters: {},
        eventFilters: {},
        historyFilters: {},
        lastEngineRun: null,
        engineRuntime: null,
        engineRuntimeError: null,
        lastCandidates: null,
        lastTrades: null,
        engineMetrics: null,
        pnlAggregate: null
    };
}
