const jsonHeaders = {
    "Content-Type": "application/json"
};

const tokenStorageKey = "funding.operatorToken";

function operatorToken() {
    return window.localStorage?.getItem(tokenStorageKey)?.trim() ?? "";
}

function withOperatorHeaders(options = {}) {
    const headers = new Headers(options.headers ?? {});
    const token = operatorToken();
    if (token) {
        headers.set("X-Operator-Token", token);
    }
    return {
        ...options,
        headers
    };
}

async function request(path, options = {}) {
    const response = await fetch(path, withOperatorHeaders(options));
    const isJson = response.headers.get("content-type")?.includes("application/json");
    const payload = isJson ? await response.json() : await response.text();

    if (!response.ok) {
        const message = typeof payload === "object" && payload?.message ? payload.message : response.statusText;
        throw new Error(message);
    }
    return payload;
}

export const api = {
    getOperatorToken() {
        return operatorToken();
    },
    setOperatorToken(token) {
        if (token?.trim()) {
            window.localStorage?.setItem(tokenStorageKey, token.trim());
        } else {
            window.localStorage?.removeItem(tokenStorageKey);
        }
    },
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
    }
};
