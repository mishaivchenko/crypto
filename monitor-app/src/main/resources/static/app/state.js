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
        pnlAggregate: null,
        layerCollapsed: {
            trade: { latency: true, health: false, execution: true },
            candidate: { liquidity: false, ai: false },
            history: { enrichment: true },
            venue: { latency: false },
        }
    };
}
