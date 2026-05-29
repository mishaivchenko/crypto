const jsonHeaders = {
    "Content-Type": "application/json"
};

const AUTH_RELOAD_KEY = "fd_auth_reloaded";

async function request(path, options = {}) {
    const response = await fetch(path, { credentials: "same-origin", ...options });
    if (response.status === 401) {
        if (typeof sessionStorage !== "undefined" && !sessionStorage.getItem(AUTH_RELOAD_KEY)) {
            sessionStorage.setItem(AUTH_RELOAD_KEY, "1");
            window.location.reload();
            return new Promise(() => {}); // never resolves — reload is in flight
        }
        const err = new Error("Session expired. Please reload the page to sign in.");
        err.isAuthError = true;
        throw err;
    }
    // Non-401 means auth layer passed — clear reload guard so future 401s get one fresh attempt.
    if (typeof sessionStorage !== "undefined") {
        sessionStorage.removeItem(AUTH_RELOAD_KEY);
    }
    const isJson = response.headers.get("content-type")?.includes("application/json");
    const payload = isJson ? await response.json() : await response.text();

    if (!response.ok) {
        const message = typeof payload === "object" && payload?.message ? payload.message : response.statusText;
        throw new Error(message);
    }
    return payload;
}

export const api = {
    getOverview() {
        return request("/api/v2/monitor/overview");
    },
    getEngineRuntime() {
        return request("/api/v2/monitor/dev/engine/runtime");
    },
    updateEngineRuntime(payload) {
        return request("/api/v2/monitor/dev/engine/runtime", {
            method: "POST",
            headers: jsonHeaders,
            body: JSON.stringify(payload)
        });
    },
    runEngineOnce(force = true) {
        const params = new URLSearchParams({ force: String(Boolean(force)) });
        return request(`/api/v2/monitor/dev/engine/run-once?${params}`, {
            method: "POST"
        });
    },
    getDevTestRunOptions() {
        return request("/api/v2/monitor/dev/test-runs/options");
    },
    createDevTestRun(payload) {
        return request("/api/v2/monitor/dev/test-runs", {
            method: "POST",
            headers: jsonHeaders,
            body: JSON.stringify(payload)
        });
    },
    runDevTestEntry(armedTradeId, payload = {}) {
        return request(`/api/v2/monitor/dev/test-runs/${armedTradeId}/entry`, {
            method: "POST",
            headers: jsonHeaders,
            body: JSON.stringify(payload)
        });
    },
    runDevTestExit(armedTradeId, payload = {}) {
        return request(`/api/v2/monitor/dev/test-runs/${armedTradeId}/exit`, {
            method: "POST",
            headers: jsonHeaders,
            body: JSON.stringify(payload)
        });
    },
    listCandidates(filters = {}) {
        const params = new URLSearchParams({ page: "0", size: "50" });
        Object.entries(filters).forEach(([key, value]) => {
            if (value) {
                params.set(key, value);
            }
        });
        return request(`/api/v1/candidates?${params}`);
    },
    getCandidate(id) {
        return request(`/api/v1/candidates/${id}`);
    },
    approveCandidate(id, payload) {
        return request(`/api/v1/candidates/${id}/approve`, {
            method: "POST",
            headers: jsonHeaders,
            body: JSON.stringify(payload)
        });
    },
    rejectCandidate(id, payload) {
        return request(`/api/v1/candidates/${id}/reject`, {
            method: "POST",
            headers: jsonHeaders,
            body: JSON.stringify(payload)
        });
    },
    deleteCandidate(id, note = null) {
        const suffix = note ? `?note=${encodeURIComponent(note)}` : "";
        return request(`/api/v1/candidates/${id}${suffix}`, {
            method: "DELETE"
        });
    },
    analyzeCandidate(id) {
        return request(`/api/v1/candidates/${id}/analyze`, {
            method: "POST"
        });
    },
    listFundingEvents(filters = {}) {
        const params = new URLSearchParams({ page: "0", size: "50" });
        Object.entries(filters).forEach(([key, value]) => {
            if (value) {
                params.set(key, value);
            }
        });
        return request(`/api/v1/funding-events?${params}`);
    },
    getFundingEvent(id) {
        return request(`/api/v1/funding-events/${id}`);
    },
    armFundingEvent(id, payload) {
        return request(`/api/v1/funding-events/${id}/arm`, {
            method: "POST",
            headers: jsonHeaders,
            body: JSON.stringify(payload)
        });
    },
    listFundingEventJournal(id) {
        return request(`/api/v1/funding-events/${id}/journal`);
    },
    updateArmedTrade(id, payload) {
        return request(`/api/v1/armed-trades/${id}`, {
            method: "PUT",
            headers: jsonHeaders,
            body: JSON.stringify(payload)
        });
    },
    cancelArmedTrade(id) {
        return request(`/api/v1/armed-trades/${id}`, { method: "DELETE" });
    },
    closeArmedTrade(id) {
        return request(`/api/v1/armed-trades/${id}/close`, { method: "POST" });
    },
    listArmedTrades(options = {}) {
        const params = new URLSearchParams();
        if (options.includeHistorical) {
            params.set("includeHistorical", "true");
        }
        const suffix = params.size ? `?${params}` : "";
        return request(`/api/v1/armed-trades${suffix}`);
    },
    getArmedTrade(id) {
        return request(`/api/v1/armed-trades/${id}`);
    },
    listArmedTradeJournal(id) {
        return request(`/api/v1/armed-trades/${id}/journal`);
    },
    listOrderAttempts(id) {
        return request(`/api/v1/armed-trades/${id}/order-attempts`);
    },
    getTradePosition(id) {
        return request(`/api/v1/armed-trades/${id}/position`).catch(() => null);
    },
    getTradeOutcome(id) {
        return request(`/api/v1/armed-trades/${id}/outcome`).catch(() => null);
    },
    getEngineMetrics() {
        return request("/api/v2/monitor/dev/engine/metrics").catch(() => null);
    },
    getPnlAggregate() {
        return request("/api/v1/outcomes/aggregate").catch(() => null);
    },
    getOutcomesByTradeIds(tradeIds) {
        if (!tradeIds || tradeIds.length === 0) {
            return Promise.resolve({});
        }
        const params = new URLSearchParams({ armedTradeIds: tradeIds.join(",") });
        return request(`/api/v1/outcomes?${params}`).catch(() => ({}));
    },
    probeVenueLatency(venue) {
        return request(`/api/v1/venues/${venue}/latency-probe`, { method: "POST" });
    },
    listAllOrderAttempts() {
        return request("/api/v1/order-attempts");
    },
    listVenues() {
        return request("/api/v1/venues");
    },
    getGlobalVenueMode() {
        return request("/api/v1/venues/access-mode");
    },
    setGlobalVenueMode(mode) {
        return request("/api/v1/venues/access-mode", {
            method: "POST",
            headers: jsonHeaders,
            body: JSON.stringify({ mode })
        });
    },
    getVenue(venue) {
        return request(`/api/v1/venues/${venue}`);
    },
    syncVenue(venue) {
        return request(`/api/v1/venues/${venue}/sync`, { method: "POST" });
    },
    checkVenueCredentials(venue) {
        return request(`/api/v1/venues/${venue}/check`, { method: "POST" });
    },
    listCredentials() {
        return request("/api/v1/operators/me/credentials");
    },
    upsertCredential(venue, mode, payload) {
        return request(`/api/v1/operators/me/credentials/${venue}/${mode}`, {
            method: "PUT",
            headers: jsonHeaders,
            body: JSON.stringify(payload)
        });
    },
    deleteCredential(venue, mode) {
        return request(`/api/v1/operators/me/credentials/${venue}/${mode}`, { method: "DELETE" });
    },
    checkCredential(venue, mode) {
        return request(`/api/v1/operators/me/credentials/${venue}/${mode}/check`, { method: "POST" });
    },
    listVenueInstruments(venue) {
        return request(`/api/v1/venues/${venue}/instruments?activeOnly=true`);
    },
    listVenueTimings(venue) {
        const suffix = venue ? `?venue=${encodeURIComponent(venue)}` : "";
        return request(`/api/v1/venues/timings${suffix}`);
    },
    setVenueDefaultLatency(venue, defaultManualLatencyAdjustmentMs) {
        return request(`/api/v1/venues/${venue}/default-latency`, {
            method: "POST",
            headers: jsonHeaders,
            body: JSON.stringify({ defaultManualLatencyAdjustmentMs })
        });
    },
    getTradeLiquidity(tradeId) {
        return request(`/api/v1/trades/${tradeId}/liquidity`).catch(() => null);
    },
    refreshTradeLiquidity(tradeId, venue, venueSymbol) {
        const params = new URLSearchParams({ venue, venueSymbol });
        return request(`/api/v1/trades/${tradeId}/refresh-liquidity?${params}`, { method: "POST" });
    },
    getCandidateLiquidity(id) {
        return request(`/api/v1/candidates/${id}/liquidity`).catch(() => null);
    },
    assessCandidateLiquidity(id) {
        return request(`/api/v1/candidates/${id}/liquidity/refresh`, { method: "POST" });
    },
    getAiStatus() {
        return request("/api/v1/ai/status");
    },
    setAiEnabled(enabled) {
        return request(enabled ? "/api/v1/ai/enable" : "/api/v1/ai/disable", { method: "POST" });
    },
    getAiPerformance() {
        return request("/api/v1/ai/performance");
    },
    assessLiquidity(venue, venueSymbol, tradeId) {
        const params = new URLSearchParams({ tradeId: String(tradeId) });
        return request(`/api/v1/venues/${venue}/symbols/${encodeURIComponent(venueSymbol)}/liquidity-assessment?${params}`, {
            method: "POST"
        });
    }
};
