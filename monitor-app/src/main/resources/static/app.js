import { api } from "/api.js";
import { emptyState, formatBadge, formatInstant, formatNumber, journalMarkup, metaRow } from "/ui.js";

const state = {
    screen: "dashboard",
    candidateFilters: {},
    eventFilters: {}
};

const nodes = {
    nav: document.getElementById("nav"),
    refreshAllButton: document.getElementById("refresh-all-button"),
    globalError: document.getElementById("global-error"),
    globalSuccess: document.getElementById("global-success"),
    screens: {
        dashboard: document.getElementById("screen-dashboard"),
        candidates: document.getElementById("screen-candidates"),
        events: document.getElementById("screen-events"),
        trades: document.getElementById("screen-trades"),
        venues: document.getElementById("screen-venues")
    },
    dashboardSummary: document.getElementById("dashboard-summary"),
    dashboardVenues: document.getElementById("dashboard-venues"),
    candidatesList: document.getElementById("candidates-list"),
    eventsList: document.getElementById("events-list"),
    tradesList: document.getElementById("trades-list"),
    venuesList: document.getElementById("venues-list"),
    candidateFilters: document.getElementById("candidate-filters"),
    eventFilters: document.getElementById("event-filters"),
    drawer: document.getElementById("detail-drawer"),
    drawerType: document.getElementById("drawer-type"),
    drawerTitle: document.getElementById("drawer-title"),
    drawerContent: document.getElementById("drawer-content"),
    drawerClose: document.getElementById("drawer-close")
};

function showBanner(target, message) {
    target.textContent = message;
    target.classList.remove("hidden");
    window.clearTimeout(target._timeoutId);
    target._timeoutId = window.setTimeout(() => target.classList.add("hidden"), 4000);
}

function showError(message) {
    showBanner(nodes.globalError, message);
}

function showSuccess(message) {
    showBanner(nodes.globalSuccess, message);
}

function setLoading(target, label = "Loading…") {
    target.innerHTML = emptyState(label);
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

function renderDashboard(overview) {
    nodes.dashboardSummary.innerHTML = `
        ${summaryCard("Pending Candidates", overview.pendingCandidates, "Needs review before event creation")}
        ${summaryCard("Funding Events", overview.fundingEvents, `${overview.discoveredEvents} still discovered`)}
        ${summaryCard("Armed Trades", overview.armedTrades, "Prepared and waiting for engine")}
        ${summaryCard("Active Venues", overview.activeVenues, `Version ${overview.version}`)}
    `;
    nodes.dashboardVenues.innerHTML = overview.venues.length
        ? overview.venues.map((venue) => venueCard(venue)).join("")
        : emptyState("No venue diagnostics available yet.");

    nodes.dashboardVenues.querySelectorAll("[data-open-venue]").forEach((button) => {
        button.addEventListener("click", () => openVenue(button.dataset.openVenue));
    });
}

function summaryCard(title, value, detail) {
    return `
        <article class="summary-card">
            <span class="eyebrow">${title}</span>
            <strong>${formatNumber(value)}</strong>
            <p class="muted">${detail}</p>
        </article>
    `;
}

function venueCard(venue) {
    return `
        <article class="list-item">
            <header>
                <div>
                    <h3 class="item-title">${venue.venue}</h3>
                    <p class="muted">mode ${venue.mode} · ${venue.activeInstrumentCount} active instruments</p>
                </div>
                <div class="actions">
                    ${formatBadge("event", venue.credentialsConfigured ? "credentials ok" : "credentials missing", venue.credentialsConfigured ? "" : "warn")}
                    <button class="button secondary" type="button" data-open-venue="${venue.venue}">Inspect</button>
                </div>
            </header>
            <div class="item-row">
                <span class="muted">Last sync ${formatInstant(venue.lastSyncedAt)}</span>
                <span class="muted">${venue.averageRequestTimeMs ?? "—"} ms avg · ${venue.requests ?? 0} req</span>
            </div>
        </article>
    `;
}

function renderCandidates(page) {
    const candidates = page?.content ?? [];
    nodes.candidatesList.innerHTML = candidates.length
        ? candidates.map((candidate) => `
            <article class="list-item">
                <header>
                    <div>
                        <h3 class="item-title">${candidate.normalizedSymbol ?? candidate.rawSymbol}</h3>
                        <p class="muted">raw ${candidate.rawSymbol}</p>
                    </div>
                    ${formatBadge("candidate", candidate.status)}
                </header>
                <div class="item-row">
                    <span class="muted">${candidate.venueHints?.join(", ") || "No venue hints"}</span>
                    <span class="muted">${formatInstant(candidate.detectedAt)}</span>
                </div>
                <div class="actions">
                    ${candidate.fundingEventId ? formatBadge("event", `event #${candidate.fundingEventId}`) : ""}
                    <button class="button secondary" type="button" data-open-candidate="${candidate.id}">Review</button>
                </div>
            </article>
        `).join("")
        : emptyState("No candidates match the current filters.");

    nodes.candidatesList.querySelectorAll("[data-open-candidate]").forEach((button) => {
        button.addEventListener("click", () => openCandidate(button.dataset.openCandidate));
    });
}

function renderFundingEvents(page) {
    const events = page?.content ?? [];
    nodes.eventsList.innerHTML = events.length
        ? events.map((event) => `
            <article class="list-item">
                <header>
                    <div>
                        <h3 class="item-title">${event.symbol}</h3>
                        <p class="muted">${event.venue} · funding ${formatInstant(event.fundingTime)}</p>
                    </div>
                    ${formatBadge("event", event.status)}
                </header>
                <div class="item-row">
                    <span class="muted">candidate ${event.signalCandidateId ?? "manual"}</span>
                    <span class="muted">rate ${event.fundingRatePct ?? "—"}</span>
                </div>
                <div class="actions">
                    <button class="button secondary" type="button" data-open-event="${event.id}">Inspect</button>
                </div>
            </article>
        `).join("")
        : emptyState("No funding events match the current filters.");

    nodes.eventsList.querySelectorAll("[data-open-event]").forEach((button) => {
        button.addEventListener("click", () => openEvent(button.dataset.openEvent));
    });
}

function renderTrades(trades) {
    nodes.tradesList.innerHTML = trades.length
        ? trades.map((trade) => `
            <article class="list-item">
                <header>
                    <div>
                        <h3 class="item-title">Trade #${trade.id}</h3>
                        <p class="muted">event ${trade.fundingEventId} · ${trade.notionalUsd} USD</p>
                    </div>
                    ${formatBadge("trade", trade.state)}
                </header>
                <div class="item-row">
                    <span class="muted">${trade.intendedSide ?? "No side"} · arm ${trade.armSource ?? "—"}</span>
                    <span class="muted">entry ${formatInstant(trade.plannedEntryAt)}</span>
                </div>
                <div class="actions">
                    <button class="button secondary" type="button" data-open-trade="${trade.id}">Inspect</button>
                </div>
            </article>
        `).join("")
        : emptyState("No armed trades yet.");

    nodes.tradesList.querySelectorAll("[data-open-trade]").forEach((button) => {
        button.addEventListener("click", () => openTrade(button.dataset.openTrade));
    });
}

function renderVenues(venues) {
    nodes.venuesList.innerHTML = venues.length
        ? venues.map((venue) => venueCard({
            venue: venue.venue,
            mode: venue.configuredMode,
            credentialsConfigured: venue.credentialsConfigured,
            activeInstrumentCount: venue.activeInstrumentCount,
            lastSyncedAt: venue.lastSyncedAt,
            averageRequestTimeMs: null,
            requests: null
        })).join("")
        : emptyState("No venues configured.");

    nodes.venuesList.querySelectorAll("[data-open-venue]").forEach((button) => {
        button.addEventListener("click", () => openVenue(button.dataset.openVenue));
    });
}

async function refreshCurrentScreen() {
    try {
        if (state.screen === "dashboard") {
            setLoading(nodes.dashboardSummary, "Loading overview…");
            setLoading(nodes.dashboardVenues, "Loading venue activity…");
            renderDashboard(await api.getOverview());
            return;
        }
        if (state.screen === "candidates") {
            setLoading(nodes.candidatesList, "Loading candidates…");
            renderCandidates(await api.listCandidates(state.candidateFilters));
            return;
        }
        if (state.screen === "events") {
            setLoading(nodes.eventsList, "Loading funding events…");
            renderFundingEvents(await api.listFundingEvents(state.eventFilters));
            return;
        }
        if (state.screen === "trades") {
            setLoading(nodes.tradesList, "Loading armed trades…");
            renderTrades(await api.listArmedTrades());
            return;
        }
        if (state.screen === "venues") {
            setLoading(nodes.venuesList, "Loading venues…");
            renderVenues(await api.listVenues());
        }
    } catch (error) {
        showError(error.message);
    }
}

async function openCandidate(id) {
    try {
        const candidate = await api.getCandidate(id);
        nodes.drawerType.textContent = "Candidate";
        nodes.drawerTitle.textContent = candidate.normalizedSymbol ?? candidate.rawSymbol;
        nodes.drawerContent.innerHTML = `
            <section class="meta-grid">
                ${metaRow("Status", formatBadge("candidate", candidate.status))}
                ${metaRow("Raw Symbol", candidate.rawSymbol)}
                ${metaRow("Normalized", candidate.normalizedSymbol)}
                ${metaRow("Detected", formatInstant(candidate.detectedAt))}
                ${metaRow("Venue Hints", candidate.venueHints?.join(", ") || "—")}
                ${metaRow("Review Decision", candidate.reviewDecision ?? "Pending")}
                ${metaRow("Funding Event", candidate.fundingEventId ? `#${candidate.fundingEventId}` : "—")}
                ${metaRow("Failure", candidate.normalizationFailureReason ?? "—")}
            </section>
            <section class="panel">
                <div class="panel-header"><h3>Approve Candidate</h3></div>
                <form class="drawer-form" data-action="approve-candidate" data-id="${candidate.id}">
                    <input name="venue" type="text" placeholder="Venue" value="${candidate.venueHints?.[0] ?? ""}">
                    <input name="symbol" type="text" placeholder="Symbol override (optional)" value="${candidate.normalizedSymbol ?? ""}">
                    <input name="fundingTime" type="datetime-local" placeholder="Funding time">
                    <input name="fundingRatePct" type="number" step="0.0001" placeholder="Funding rate pct">
                    <textarea name="reviewNotes" placeholder="Review notes"></textarea>
                    <div class="actions">
                        <button class="button" type="submit">Approve → Event</button>
                    </div>
                </form>
            </section>
            <section class="panel">
                <div class="panel-header"><h3>Reject Candidate</h3></div>
                <form class="drawer-form" data-action="reject-candidate" data-id="${candidate.id}">
                    <textarea name="reviewNotes" placeholder="Why reject this candidate?"></textarea>
                    <div class="actions">
                        <button class="button danger" type="submit">Reject</button>
                    </div>
                </form>
            </section>
        `;
    } catch (error) {
        showError(error.message);
    }
}

async function openEvent(id) {
    try {
        const [event, journal] = await Promise.all([
            api.getFundingEvent(id),
            api.listFundingEventJournal(id)
        ]);
        nodes.drawerType.textContent = "Funding Event";
        nodes.drawerTitle.textContent = `${event.symbol} · ${event.venue}`;
        nodes.drawerContent.innerHTML = `
            <section class="meta-grid">
                ${metaRow("Status", formatBadge("event", event.status))}
                ${metaRow("Funding Time", formatInstant(event.fundingTime))}
                ${metaRow("Funding Rate", event.fundingRatePct ?? "—")}
                ${metaRow("Source", event.sourceType)}
                ${metaRow("Candidate", event.signalCandidateId ? `#${event.signalCandidateId}` : "Manual")}
            </section>
            <section class="panel">
                <div class="panel-header"><h3>Arm Trade</h3></div>
                <form class="drawer-form" data-action="arm-event" data-id="${event.id}">
                    <input name="notionalUsd" type="number" step="0.01" placeholder="Notional USD" value="25">
                    <select name="intendedSide">
                        <option value="LONG">LONG</option>
                        <option value="SHORT">SHORT</option>
                    </select>
                    <input name="plannedEntryAt" type="datetime-local">
                    <input name="plannedExitAt" type="datetime-local">
                    <textarea name="notes" placeholder="Trade preparation notes"></textarea>
                    <div class="actions">
                        <button class="button" type="submit">Arm Trade</button>
                    </div>
                </form>
            </section>
            <section class="panel">
                <div class="panel-header"><h3>Journal</h3></div>
                ${journalMarkup(journal)}
            </section>
        `;
    } catch (error) {
        showError(error.message);
    }
}

async function openTrade(id) {
    try {
        const [trade, journal] = await Promise.all([
            api.getArmedTrade(id),
            api.listArmedTradeJournal(id)
        ]);
        nodes.drawerType.textContent = "Armed Trade";
        nodes.drawerTitle.textContent = `Trade #${trade.id}`;
        nodes.drawerContent.innerHTML = `
            <section class="meta-grid">
                ${metaRow("State", formatBadge("trade", trade.state))}
                ${metaRow("Funding Event", `#${trade.fundingEventId}`)}
                ${metaRow("Notional", `${trade.notionalUsd} USD`)}
                ${metaRow("Side", trade.intendedSide ?? "—")}
                ${metaRow("Planned Entry", formatInstant(trade.plannedEntryAt))}
                ${metaRow("Planned Exit", formatInstant(trade.plannedExitAt))}
                ${metaRow("Armed At", formatInstant(trade.armedAt))}
                ${metaRow("Entry Lead", trade.entryLeadMs ?? "—")}
                ${metaRow("Exit Lead", trade.exitLeadMs ?? "—")}
                ${metaRow("Arm Source", trade.armSource ?? "—")}
                ${metaRow("Notes", trade.notes ?? "—")}
            </section>
            <section class="panel">
                <div class="panel-header"><h3>Journal</h3></div>
                ${journalMarkup(journal)}
            </section>
        `;
    } catch (error) {
        showError(error.message);
    }
}

async function openVenue(venueName) {
    try {
        const [venue, instruments, timings] = await Promise.all([
            api.getVenue(venueName),
            api.listVenueInstruments(venueName),
            api.listVenueTimings(venueName)
        ]);
        nodes.drawerType.textContent = "Venue";
        nodes.drawerTitle.textContent = venue.venue;
        nodes.drawerContent.innerHTML = `
            <section class="meta-grid">
                ${metaRow("Mode", venue.configuredMode)}
                ${metaRow("Credentials", venue.credentialsConfigured ? "Configured" : "Missing")}
                ${metaRow("Metadata URL", venue.metadataBaseUrl ?? "—")}
                ${metaRow("Active Instruments", venue.activeInstrumentCount)}
                ${metaRow("Last Synced", formatInstant(venue.lastSyncedAt))}
            </section>
            <section class="panel">
                <div class="panel-header">
                    <h3>Actions</h3>
                    <button class="button secondary" type="button" data-action="sync-venue" data-venue="${venue.venue}">Sync Now</button>
                </div>
                <p class="muted">Manual sync refreshes instrument metadata and updates venue diagnostics.</p>
            </section>
            <section class="panel">
                <div class="panel-header"><h3>Instruments</h3></div>
                ${instruments.length ? instruments.map((instrument) => `
                    <div class="meta-row">
                        <span class="eyebrow">${instrument.venueSymbol}</span>
                        <strong>${instrument.canonicalSymbol}</strong>
                        <span class="muted">${instrument.status} · step ${instrument.qtyStep ?? "—"} · min ${instrument.minOrderQty ?? "—"}</span>
                    </div>
                `).join("") : emptyState("No synced instruments yet.")}
            </section>
            <section class="panel">
                <div class="panel-header"><h3>Request Timings</h3></div>
                ${timings.length ? timings.map((timing) => `
                    <div class="meta-row">
                        <span class="eyebrow">${timing.operation}</span>
                        <strong>${timing.averageDurationMs} ms avg</strong>
                        <span class="muted">${timing.requests} req · ${timing.successes} ok · last ${formatInstant(timing.lastOccurredAt)}</span>
                    </div>
                `).join("") : emptyState("No timing samples for this venue yet.")}
            </section>
        `;
    } catch (error) {
        showError(error.message);
    }
}

async function handleDrawerAction(event) {
    if (event.type === "submit") {
        const form = event.target.closest("form.drawer-form");
        if (!form) {
            return;
        }
        event.preventDefault();
        const data = new FormData(form);
        const action = form.dataset.action;
        try {
            if (action === "approve-candidate") {
                await api.approveCandidate(form.dataset.id, {
                    venue: data.get("venue") || null,
                    symbol: data.get("symbol") || null,
                    fundingTime: toIsoOrNull(data.get("fundingTime")),
                    fundingRatePct: numberOrNull(data.get("fundingRatePct")),
                    reviewNotes: data.get("reviewNotes") || null
                });
                showSuccess("Candidate approved and converted into FundingEvent.");
                await Promise.all([refreshCurrentScreen(), openCandidate(form.dataset.id)]);
                return;
            }
            if (action === "reject-candidate") {
                await api.rejectCandidate(form.dataset.id, {
                    reviewNotes: data.get("reviewNotes") || null
                });
                showSuccess("Candidate rejected.");
                await Promise.all([refreshCurrentScreen(), openCandidate(form.dataset.id)]);
                return;
            }
            if (action === "arm-event") {
                await api.armFundingEvent(form.dataset.id, {
                    notionalUsd: numberOrNull(data.get("notionalUsd")),
                    intendedSide: data.get("intendedSide") || null,
                    plannedEntryAt: toIsoOrNull(data.get("plannedEntryAt")),
                    plannedExitAt: toIsoOrNull(data.get("plannedExitAt")),
                    notes: data.get("notes") || null
                });
                showSuccess("Funding event armed successfully.");
                await Promise.all([refreshCurrentScreen(), openEvent(form.dataset.id)]);
            }
        } catch (error) {
            showError(error.message);
        }
        return;
    }

    const syncButton = event.target.closest("[data-action='sync-venue']");
    if (syncButton) {
        try {
            await api.syncVenue(syncButton.dataset.venue);
            showSuccess(`Venue ${syncButton.dataset.venue} synced.`);
            await Promise.all([refreshCurrentScreen(), openVenue(syncButton.dataset.venue)]);
        } catch (error) {
            showError(error.message);
        }
    }
}

function toIsoOrNull(value) {
    if (!value) {
        return null;
    }
    return new Date(value).toISOString();
}

function numberOrNull(value) {
    if (value === null || value === undefined || value === "") {
        return null;
    }
    const parsed = Number(value);
    return Number.isNaN(parsed) ? null : parsed;
}

nodes.nav.addEventListener("click", (event) => {
    const button = event.target.closest(".nav-link");
    if (!button) {
        return;
    }
    switchScreen(button.dataset.screen);
});

nodes.refreshAllButton.addEventListener("click", async () => {
    await refreshCurrentScreen();
    showSuccess("Screen refreshed.");
});

nodes.candidateFilters.addEventListener("submit", async (event) => {
    event.preventDefault();
    state.candidateFilters = Object.fromEntries(new FormData(event.currentTarget).entries());
    await refreshCurrentScreen();
});

nodes.eventFilters.addEventListener("submit", async (event) => {
    event.preventDefault();
    state.eventFilters = Object.fromEntries(new FormData(event.currentTarget).entries());
    await refreshCurrentScreen();
});

nodes.drawerClose.addEventListener("click", () => {
    nodes.drawerType.textContent = "Detail";
    nodes.drawerTitle.textContent = "Select an item";
    nodes.drawerContent.innerHTML = `<p class="muted">Pick a candidate, event, trade, or venue to inspect details.</p>`;
});

nodes.drawerContent.addEventListener("submit", handleDrawerAction);
nodes.drawerContent.addEventListener("click", handleDrawerAction);

await refreshCurrentScreen();
