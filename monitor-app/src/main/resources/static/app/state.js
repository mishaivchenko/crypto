export function createAppState() {
    return {
        screen: "dashboard",
        candidateFilters: {},
        eventFilters: {},
        historyFilters: {},
        lastEngineRun: null,
        engineRuntime: null,
        engineRuntimeError: null
    };
}
