const jsonHeaders = {
    "Content-Type": "application/json"
};

async function request(path, options = {}) {
    const response = await fetch(path, options);
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
    listArmedTrades() {
        return request("/api/v1/armed-trades");
    },
    getArmedTrade(id) {
        return request(`/api/v1/armed-trades/${id}`);
    },
    listArmedTradeJournal(id) {
        return request(`/api/v1/armed-trades/${id}/journal`);
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
    listVenueInstruments(venue) {
        return request(`/api/v1/venues/${venue}/instruments?activeOnly=true`);
    },
    listVenueTimings(venue) {
        const suffix = venue ? `?venue=${encodeURIComponent(venue)}` : "";
        return request(`/api/v1/venues/timings${suffix}`);
    }
};
