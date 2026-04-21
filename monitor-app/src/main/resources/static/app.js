import { api } from "/api.js";
import {
    filterHistoryTrades,
    historyTradeRow,
    tradeHistoryDetailMarkup
} from "/history.js";
import {
    emptyState,
    escapeHtml,
    formatBadge,
    formatConnectionBadge,
    formatDecimal,
    formatDurationMs,
    formatFundingCountdown,
    formatInstant,
    formatNumber,
    formatRelative,
    formatSignedMs,
    journalMarkup,
    kv,
    metaRow,
    section
} from "/ui.js";

const state = {
    screen: "dashboard",
    candidateFilters: {},
    eventFilters: {},
    historyFilters: {}
};

const nodes = {
    nav: document.getElementById("nav"),
    refreshAllButton: document.getElementById("refresh-all-button"),
    operatorTokenForm: document.getElementById("operator-token-form"),
    operatorTokenInput: document.getElementById("operator-token-input"),
    globalModeForm: document.getElementById("global-mode-form"),
    globalModeSelect: document.getElementById("global-mode-select"),
    globalError: document.getElementById("global-error"),
    globalSuccess: document.getElementById("global-success"),
    screens: {
        dashboard: document.getElementById("screen-dashboard"),
        candidates: document.getElementById("screen-candidates"),
        events: document.getElementById("screen-events"),
        trades: document.getElementById("screen-trades"),
        history: document.getElementById("screen-history"),
        venues: document.getElementById("screen-venues")
    },
    dashboardSummary: document.getElementById("dashboard-summary"),
    dashboardVenues: document.getElementById("dashboard-venues"),
    candidatesList: document.getElementById("candidates-list"),
    eventsList: document.getElementById("events-list"),
    tradesList: document.getElementById("trades-list"),
    historyList: document.getElementById("history-list"),
    historyCount: document.getElementById("history-count"),
    venuesList: document.getElementById("venues-list"),
    candidateFilters: document.getElementById("candidate-filters"),
    eventFilters: document.getElementById("event-filters"),
    historyFilters: document.getElementById("history-filters"),
    drawerType: document.getElementById("drawer-type"),
    drawerTitle: document.getElementById("drawer-title"),
    drawerContent: document.getElementById("drawer-content"),
    drawerClose: document.getElementById("drawer-close")
};

function sourceLabel(value) {
    if (!value) {
        return "—";
    }
    const normalized = String(value).toLowerCase();
    if (normalized === "funding_api" || normalized === "funding-api") {
        return "API фандинга";
    }
    if (normalized === "manual") {
        return "manual";
    }
    return String(value);
}

function sideLabel(value) {
    if (!value) {
        return "Side не задан";
    }
    return value === "LONG" ? "Long" : value === "SHORT" ? "Short" : value;
}

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

function credentialsBadge(venue) {
    if (venue.credentialsConfigured) {
        return formatBadge("venue", "Keys OK", "good");
    }
    return formatBadge("venue", venue.credentialsRequired ? "Нет keys" : "Keys empty", "warning");
}

function venueHealthBadge(venue) {
    if (!venue.activeInstrumentCount) {
        return formatBadge("venue", "No instruments", "warning");
    }
    if (!venue.lastSyncedAt) {
        return formatBadge("venue", "No sync", "warning");
    }
    return formatBadge("venue", "Registry ready", "good");
}

function modeLabel(mode) {
    if (!mode) {
        return "mode не задан";
    }
    return mode === "testnet" ? "Testnet" : mode === "production" ? "Production" : String(mode);
}

function connectionLine(venue) {
    const message = venue.connectionMessage ? escapeHtml(venue.connectionMessage) : "Проверка ещё не запускалась.";
    const http = venue.lastConnectionHttpStatus ? ` · HTTP ${venue.lastConnectionHttpStatus}` : "";
    return `${message}${http}`;
}

function summaryCard(title, value, detail, tone = "neutral", rawValue = false) {
    return `
        <article class="summary-card tone-${tone}">
            <span class="eyebrow">${escapeHtml(title)}</span>
            <strong>${rawValue ? escapeHtml(value) : formatNumber(value)}</strong>
            <p class="muted">${escapeHtml(detail)}</p>
        </article>
    `;
}

function venueCard(venue) {
    return `
        <article class="list-item venue-card">
            <header>
                <div>
                    <h3 class="item-title">${escapeHtml(venue.venue)}</h3>
                    <p class="muted">${escapeHtml(modeLabel(venue.mode ?? venue.configuredMode))} · ${formatNumber(venue.activeInstrumentCount)} active instruments</p>
                </div>
                <div class="actions">
                    ${credentialsBadge(venue)}
                    ${formatConnectionBadge(venue.connectionStatus)}
                    ${venueHealthBadge(venue)}
                    <button class="button secondary" type="button" data-open-venue="${escapeHtml(venue.venue)}">Открыть</button>
                </div>
            </header>
            <div class="item-row">
                <span class="muted">${connectionLine(venue)}</span>
                <span class="muted">Last sync ${formatInstant(venue.lastSyncedAt)} · avg ${formatDurationMs(venue.averageRequestTimeMs)} · req ${formatNumber(venue.requests ?? 0)}</span>
            </div>
        </article>
    `;
}

function candidateStateLine(candidate) {
    if (candidate.fundingEventId) {
        return `Связан с Funding Event #${candidate.fundingEventId}`;
    }
    if (candidate.normalizationFailureReason) {
        return candidate.normalizationFailureReason;
    }
    if (candidate.venueHints?.length) {
        return `Venue hints: ${candidate.venueHints.join(", ")}`;
    }
    return "Ожидает operator review";
}

function candidateCard(candidate) {
    return `
        <article class="list-item signal-card">
            <header>
                <div>
                    <h3 class="item-title">${escapeHtml(candidate.normalizedSymbol ?? candidate.rawSymbol)}</h3>
                    <p class="muted">source ${escapeHtml(candidate.sourceVenue ?? sourceLabel(candidate.sourceType))} · raw ${escapeHtml(candidate.rawSymbol)}</p>
                </div>
                <div class="actions">
                    ${formatBadge("candidate", candidate.status)}
                    <button class="button secondary" type="button" data-open-candidate="${candidate.id}">Inspect</button>
                </div>
            </header>
            <div class="item-row">
                <span class="muted">${escapeHtml(candidateStateLine(candidate))}</span>
                <span class="muted">${formatInstant(candidate.detectedAt)} · ${formatRelative(candidate.detectedAt)}</span>
            </div>
        </article>
    `;
}

function eventCard(event) {
    return `
        <article class="list-item event-card">
            <header>
                <div>
                    <h3 class="item-title">${escapeHtml(event.symbol)}</h3>
                    <p class="muted">${escapeHtml(event.venue)} · funding ${formatInstant(event.fundingTime)}</p>
                </div>
                <div class="actions">
                    ${formatBadge("event", event.status)}
                    <button class="button secondary" type="button" data-open-event="${event.id}">Inspect</button>
                </div>
            </header>
            <div class="item-row">
                <span class="muted">signal ${event.signalCandidateId ?? "manual"} · rate ${formatDecimal(event.fundingRatePct, 6)}</span>
                <span class="muted">${formatFundingCountdown(event.fundingTime)}</span>
            </div>
        </article>
    `;
}

function tradeCard(trade) {
    return `
        <article class="list-item trade-card">
            <header>
                <div>
                    <h3 class="item-title">${escapeHtml(trade.symbol ?? `Сделка #${trade.id}`)}</h3>
                    <p class="muted">${escapeHtml(trade.venue ?? "venue не задана")} · event ${trade.fundingEventId} · ${formatDecimal(trade.notionalUsd, 2)} USD</p>
                </div>
                <div class="actions">
                    ${formatBadge("trade", trade.state)}
                    <button class="button secondary" type="button" data-open-trade="${trade.id}">Inspect</button>
                </div>
            </header>
            <div class="item-row">
                <span class="muted">${escapeHtml(sideLabel(trade.intendedSide))} · ${formatNumber(trade.entryAttemptCount ?? 1)} attempts · spacing ${formatDurationMs(trade.entrySpacingMs ?? 0)}</span>
                <span class="muted">entry ${formatInstant(trade.plannedEntryAt)} · exit ${formatInstant(trade.plannedExitAt)}</span>
            </div>
        </article>
    `;
}

function renderDashboard(overview) {
    nodes.globalModeSelect.value = String(overview.globalAccessMode ?? "TESTNET").toUpperCase();
    nodes.dashboardSummary.innerHTML = `
        ${summaryCard("Signal Queue", overview.pendingCandidates, "Очередь на operator review", "info")}
        ${summaryCard("Funding Events", overview.fundingEvents, `${overview.discoveredEvents} ещё не armed`, "warning")}
        ${summaryCard("Prepared Trades", overview.armedTrades, "Подготовлены для engine", "good")}
        ${summaryCard("Access mode", String(overview.globalAccessMode ?? "testnet").toUpperCase(), `${overview.activeVenues} venues · build ${overview.version}`, "neutral", true)}
    `;

    nodes.dashboardVenues.innerHTML = overview.venues.length
        ? overview.venues.map((venue) => venueCard(venue)).join("")
        : emptyState("Venue diagnostics пока пуст.", "Сделай sync, чтобы подтянуть instrument metadata.");

    wireOpenButtons(nodes.dashboardVenues, "[data-open-venue]", openVenue);
}

function renderCandidates(page) {
    const candidates = page?.content ?? [];
    nodes.candidatesList.innerHTML = candidates.length
        ? candidates.map(candidateCard).join("")
        : emptyState("Signal Queue пуста.", "Новые candidates из Funding API появятся здесь.");

    wireOpenButtons(nodes.candidatesList, "[data-open-candidate]", openCandidate);
}

function renderFundingEvents(page) {
    const events = page?.content ?? [];
    nodes.eventsList.innerHTML = events.length
        ? events.map(eventCard).join("")
        : emptyState("Funding Events пока нет.", "Approve signal, чтобы создать первое событие.");

    wireOpenButtons(nodes.eventsList, "[data-open-event]", openEvent);
}

function renderTrades(trades) {
    nodes.tradesList.innerHTML = trades.length
        ? trades.map(tradeCard).join("")
        : emptyState("Prepared Trades пока нет.", "Arm Funding Event, чтобы создать первую подготовленную сделку.");

    wireOpenButtons(nodes.tradesList, "[data-open-trade]", openTrade);
}

function renderHistory(trades, attemptsByTrade = {}) {
    const filtered = filterHistoryTrades(trades, state.historyFilters, attemptsByTrade);
    nodes.historyCount.textContent = `${formatNumber(filtered.length)} / ${formatNumber(trades.length)} trades`;
    nodes.historyList.innerHTML = filtered.length
        ? filtered.map((trade) => historyTradeRow(trade, attemptsByTrade[trade.id] ?? [])).join("")
        : emptyState("История сделок пуста.", "Когда появятся armed trades, здесь будет разбор Signal -> Decision -> Plan -> Attempts -> Outcome.");

    nodes.historyList.querySelectorAll("[data-open-history-trade]").forEach((row) => {
        row.addEventListener("click", () => openHistoryTrade(row.dataset.openHistoryTrade));
    });
}

function renderVenues(venues) {
    nodes.venuesList.innerHTML = venues.length
        ? venues.map(venueCard).join("")
        : emptyState("Venue Access пуст.", "Проверь enabled venues и registry sync.");

    wireOpenButtons(nodes.venuesList, "[data-open-venue]", openVenue);
}

function wireOpenButtons(container, selector, handler) {
    container.querySelectorAll(selector).forEach((button) => {
        button.addEventListener("click", () => handler(button.dataset.openCandidate || button.dataset.openEvent || button.dataset.openTrade || button.dataset.openVenue));
    });
}

async function refreshCurrentScreen() {
    try {
        if (state.screen === "dashboard") {
            setLoading(nodes.dashboardSummary, "Собираю срез контура…");
            setLoading(nodes.dashboardVenues, "Собираю пульс площадок…");
            renderDashboard(await api.getOverview());
            return;
        }
        if (state.screen === "candidates") {
            setLoading(nodes.candidatesList, "Загружаю входящие сигналы…");
            renderCandidates(await api.listCandidates(state.candidateFilters));
            return;
        }
        if (state.screen === "events") {
            setLoading(nodes.eventsList, "Загружаю подтверждённые события…");
            renderFundingEvents(await api.listFundingEvents(state.eventFilters));
            return;
        }
        if (state.screen === "trades") {
            setLoading(nodes.tradesList, "Загружаю подготовленные сделки…");
            renderTrades(await api.listArmedTrades());
            return;
        }
        if (state.screen === "history") {
            setLoading(nodes.historyList, "Собираю историю сделок…");
            const [trades, attempts] = await Promise.all([
                api.listArmedTrades({ includeHistorical: true }),
                api.listAllOrderAttempts()
            ]);
            renderHistory(trades, groupAttemptsByTrade(attempts));
            return;
        }
        if (state.screen === "venues") {
            setLoading(nodes.venuesList, "Загружаю диагностику площадок…");
            renderVenues(await api.listVenues());
        }
    } catch (error) {
        showError(error.message);
    }
}

function groupAttemptsByTrade(attempts) {
    return (attempts ?? []).reduce((acc, attempt) => {
        const key = String(attempt.armedTradeId ?? "");
        if (!acc[key]) {
            acc[key] = [];
        }
        acc[key].push(attempt);
        return acc;
    }, {});
}

function recommendationRows(candidate) {
    return [
        kv("Source", candidate.sourceVenue ?? sourceLabel(candidate.sourceType)),
        kv("Canonical symbol", candidate.normalizedSymbol ?? "Не определён"),
        kv("Suggested venue", candidate.suggestedVenue ?? candidate.sourceVenue ?? "Manual select"),
        kv("Suggested funding time", formatInstant(candidate.suggestedFundingTime)),
        kv("Funding rate", formatDecimal(candidate.suggestedFundingRatePct, 6))
    ].join("");
}

function buildApproveSection(candidate) {
    if (candidate.status === "EVENT_CREATED") {
        return section(
            "Funding Event уже создан",
            `
                <div class="action-card primary">
                    <p class="helper-text">Этот signal уже переведён в Funding Event #${escapeHtml(candidate.fundingEventId)}.</p>
                </div>
            `
        );
    }
    if (candidate.status === "REJECTED") {
        return section(
            "Candidate закрыт",
            `
                <div class="action-card">
                    <p class="helper-text">Signal уже отклонён и больше не участвует в operator flow.</p>
                </div>
            `
        );
    }

    const venue = candidate.suggestedVenue ?? candidate.sourceVenue ?? candidate.venueHints?.[0] ?? "";
    const symbol = candidate.normalizedSymbol ?? candidate.rawSymbol ?? "";
    const fundingTime = toLocalInputValue(candidate.suggestedFundingTime);
    const fundingRatePct = candidate.suggestedFundingRatePct ?? "";
    const actionLabel = candidate.status === "FAILED" ? "Исправить и create Event" : "Approve → Funding Event";
    const helper = candidate.status === "FAILED"
        ? "Signal нужно поправить вручную, прежде чем переводить его в Funding Event."
        : "Используй suggested venue и funding snapshot или переопредели поля перед approve.";

    return section(
        candidate.status === "FAILED" ? "Repair candidate" : "Approve to Funding Event",
        `
            <div class="action-card primary">
                <p class="helper-text">${escapeHtml(helper)}</p>
                <div class="detail-grid action-note">
                    ${recommendationRows(candidate)}
                </div>
                <form class="drawer-form" data-action="approve-candidate" data-id="${candidate.id}">
                    <div class="drawer-form-row labeled-row">
                        <label class="field">
                            <span>Venue</span>
                            <input name="venue" type="text" placeholder="Например, gate" value="${escapeHtml(venue)}">
                        </label>
                        <label class="field">
                            <span>Canonical symbol</span>
                            <input name="symbol" type="text" placeholder="Например, NOM/USDT" value="${escapeHtml(symbol)}">
                        </label>
                    </div>
                    <div class="drawer-form-row labeled-row">
                        <label class="field">
                            <span>Funding time</span>
                            <input name="fundingTime" type="datetime-local" value="${escapeHtml(fundingTime)}">
                        </label>
                        <label class="field">
                            <span>Funding rate, %</span>
                            <input name="fundingRatePct" type="number" step="0.000001" placeholder="-0.012500" value="${escapeHtml(fundingRatePct)}">
                        </label>
                    </div>
                    <label class="field">
                        <span>Operator note</span>
                        <textarea name="reviewNotes" placeholder="Почему этот signal стоит двигать дальше">${escapeHtml(candidate.reviewNotes ?? "")}</textarea>
                    </label>
                    <div class="actions">
                        <button class="button" type="submit">${escapeHtml(actionLabel)}</button>
                    </div>
                </form>
            </div>
        `
    );
}

function buildRejectSection(candidate) {
    if (candidate.status === "REJECTED" || candidate.status === "EVENT_CREATED") {
        return "";
    }
    return section(
        "Отклонить сигнал",
        `
            <div class="action-card">
                <p class="helper-text">Reject только если signal точно не должен становиться Funding Event.</p>
                <form class="drawer-form" data-action="reject-candidate" data-id="${candidate.id}">
                    <label class="field">
                        <span>Reject note</span>
                        <textarea name="reviewNotes" placeholder="Коротко зафиксируй причину отказа"></textarea>
                    </label>
                    <div class="actions">
                        <button class="button danger" type="submit">Reject candidate</button>
                    </div>
                </form>
            </div>
        `
    );
}

function buildDeleteCandidateSection(candidate, label = "Delete candidate") {
    return section(
        "Очистка pipeline",
        `
            <div class="action-card danger-zone">
                <p class="helper-text">Удаление candidate чистит связанный Funding Event, Prepared Trade и связанные journal entries. Source signal при этом не исполняется и не уходит в engine.</p>
                <form class="drawer-form" data-action="delete-candidate" data-id="${candidate.id}">
                    <label class="field">
                        <span>Delete note</span>
                        <textarea name="deleteNote" placeholder="Например: false signal / duplicate / operator cleanup">operator cleanup</textarea>
                    </label>
                    <div class="actions">
                        <button class="button danger" type="submit">${escapeHtml(label)}</button>
                    </div>
                </form>
            </div>
        `
    );
}

async function openCandidate(id) {
    try {
        const candidate = await api.getCandidate(id);
        nodes.drawerType.textContent = "Signal";
        nodes.drawerTitle.textContent = candidate.normalizedSymbol ?? candidate.rawSymbol;
        nodes.drawerContent.innerHTML = `
            ${section("Signal snapshot", `
                <div class="meta-grid">
                    ${metaRow("Статус", formatBadge("candidate", candidate.status))}
                    ${metaRow("Detected at", formatInstant(candidate.detectedAt), formatRelative(candidate.detectedAt))}
                    ${metaRow("Source venue", escapeHtml(candidate.sourceVenue ?? "—"))}
                    ${metaRow("Raw symbol", escapeHtml(candidate.rawSymbol))}
                    ${metaRow("Canonical symbol", escapeHtml(candidate.normalizedSymbol ?? "—"))}
                    ${metaRow("Venue hints", escapeHtml(candidate.venueHints?.join(", ") || "—"))}
                    ${metaRow("Review", escapeHtml(candidate.reviewDecision ?? "Pending"))}
                    ${metaRow("Funding Event", candidate.fundingEventId ? `#${candidate.fundingEventId}` : "—")}
                </div>
            `)}
            ${candidate.normalizationFailureReason ? section("Normalization note", `
                <div class="action-card">
                    <p class="helper-text">${escapeHtml(candidate.normalizationFailureReason)}</p>
                </div>
            `) : ""}
            ${buildApproveSection(candidate)}
            ${buildRejectSection(candidate)}
            ${candidate.status !== "DELETED" ? buildDeleteCandidateSection(candidate) : ""}
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

        const defaultEntry = toLocalInputValue(offsetIso(event.fundingTime, -45));
        const defaultExit = toLocalInputValue(offsetIso(event.fundingTime, 90));
        const canArm = event.status === "DISCOVERED";

        nodes.drawerType.textContent = "Funding Event";
        nodes.drawerTitle.textContent = `${event.symbol} · ${event.venue}`;
        nodes.drawerContent.innerHTML = `
            ${section("Event snapshot", `
                <div class="meta-grid">
                    ${metaRow("Статус", formatBadge("event", event.status))}
                    ${metaRow("Funding time", formatInstant(event.fundingTime), formatFundingCountdown(event.fundingTime))}
                    ${metaRow("Funding rate", formatDecimal(event.fundingRatePct, 6))}
                    ${metaRow("Venue", escapeHtml(event.venue))}
                    ${metaRow("Source", escapeHtml(sourceLabel(event.sourceType)))}
                    ${metaRow("Linked signal", event.signalCandidateId ? `#${event.signalCandidateId}` : "manual")}
                </div>
            `)}
            ${section("Arm Prepared Trade", canArm ? `
                <div class="action-card primary">
                    <p class="helper-text">Создай Prepared Trade для engine flow. Реальный order здесь не ставится.</p>
                    <form class="drawer-form" data-action="arm-event" data-id="${event.id}">
                        <div class="drawer-form-row labeled-row">
                            <label class="field">
                                <span>Notional, USD</span>
                                <input name="notionalUsd" type="number" step="0.01" placeholder="25" value="25">
                            </label>
                            <label class="field">
                                <span>Side</span>
                                <input name="intendedSide" type="text" value="SHORT" readonly>
                                <small>Funding strategy is SHORT-only.</small>
                            </label>
                        </div>
                        <div class="drawer-form-row labeled-row">
                            <label class="field">
                                <span>Planned entry</span>
                                <input name="plannedEntryAt" type="datetime-local" value="${escapeHtml(defaultEntry)}">
                            </label>
                            <label class="field">
                                <span>Planned exit</span>
                                <input name="plannedExitAt" type="datetime-local" value="${escapeHtml(defaultExit)}">
                            </label>
                        </div>
                        <div class="drawer-form-row labeled-row">
                            <label class="field">
                                <span>Entry attempts</span>
                                <input name="entryAttemptCount" type="number" min="1" max="25" step="1" value="3">
                            </label>
                            <label class="field">
                                <span>Spacing, ms</span>
                                <input name="entrySpacingMs" type="number" min="0" step="1" value="150">
                            </label>
                        </div>
                        <label class="field">
                            <span>Manual latency adj, ms</span>
                            <input name="manualLatencyAdjustmentMs" type="number" min="-60000" max="60000" step="1" value="0">
                            <small>Engine will trigger attempts earlier by measured latency plus this adjustment.</small>
                        </label>
                        <label class="field">
                            <span>Preparation note</span>
                            <textarea name="notes" placeholder="Почему этот Event должен перейти в Prepared Trade"></textarea>
                        </label>
                        <div class="actions">
                            <button class="button" type="submit">Create Prepared Trade</button>
                        </div>
                    </form>
                </div>
            ` : `
                <div class="action-card">
                    <p class="helper-text">Event уже находится в статусе ${escapeHtml(event.status.toLowerCase())} и больше не может быть armed из этого desk.</p>
                </div>
            `)}
            ${event.signalCandidateId ? buildDeleteCandidateSection({ id: event.signalCandidateId }, "Delete source signal") : ""}
            ${section("Journal", journalMarkup(journal))}
        `;
    } catch (error) {
        showError(error.message);
    }
}

async function openTrade(id) {
    try {
        const [trade, journal, attempts] = await Promise.all([
            api.getArmedTrade(id),
            api.listArmedTradeJournal(id),
            api.listOrderAttempts(id)
        ]);
        nodes.drawerType.textContent = "Prepared Trade";
        nodes.drawerTitle.textContent = trade.symbol ? `${trade.symbol} · ${trade.venue}` : `Сделка #${trade.id}`;
        nodes.drawerContent.innerHTML = `
            ${section("Trade parameters", `
                <div class="meta-grid">
                    ${metaRow("Статус", formatBadge("trade", trade.state))}
                    ${metaRow("Funding Event", `#${trade.fundingEventId}`)}
                    ${metaRow("Source signal", trade.signalCandidateId ? `#${trade.signalCandidateId}` : "manual")}
                    ${metaRow("Venue", escapeHtml(trade.venue ?? "—"))}
                    ${metaRow("Instrument", escapeHtml(trade.symbol ?? "—"))}
                    ${metaRow("Funding time", formatInstant(trade.fundingTime), formatFundingCountdown(trade.fundingTime))}
                    ${metaRow("Notional", `${formatDecimal(trade.notionalUsd, 2)} USD`)}
                    ${metaRow("Side", escapeHtml(sideLabel(trade.intendedSide)))}
                    ${metaRow("Planned entry", formatInstant(trade.plannedEntryAt))}
                    ${metaRow("Planned exit", formatInstant(trade.plannedExitAt))}
                    ${metaRow("Entry attempts", formatNumber(trade.entryAttemptCount ?? 1), `spacing ${formatDurationMs(trade.entrySpacingMs ?? 0)}`)}
                    ${metaRow("Measured latency", formatDurationMs(trade.measuredEntryLatencyMs))}
                    ${metaRow("Manual latency adj", formatSignedMs(trade.manualLatencyAdjustmentMs ?? 0))}
                    ${metaRow("Effective trigger lead", formatDurationMs(trade.effectiveEntryLatencyMs ?? 0))}
                    ${metaRow("Armed at", formatInstant(trade.armedAt))}
                    ${metaRow("Entry lead", formatDurationMs(trade.entryLeadMs))}
                    ${metaRow("Exit lead", formatDurationMs(trade.exitLeadMs))}
                    ${metaRow("Arm source", escapeHtml(trade.armSource ?? "—"))}
                    ${metaRow("Note", escapeHtml(trade.notes ?? "—"))}
                </div>
            `)}
            ${section("Execution attempts", attempts.length ? attempts.map((attempt) => `
                <div class="meta-row">
                    <span class="meta-label">#${escapeHtml(attempt.attemptNumber ?? "—")} · ${escapeHtml(attempt.status)}</span>
                    <strong class="meta-value">${escapeHtml(attempt.symbol)} ${escapeHtml(attempt.side)}</strong>
                    <span class="meta-helper">${escapeHtml(attempt.failureReason ?? "Без ошибки")} · trigger ${formatInstant(attempt.triggerAt)} · recorded ${formatInstant(attempt.createdAt)}</span>
                </div>
            `).join("") : emptyState("Execution attempts пока нет.", "Запусти engine run-once, чтобы увидеть FAILED/SUBMITTED попытки."))}
            ${trade.signalCandidateId ? buildDeleteCandidateSection({ id: trade.signalCandidateId }, "Delete source signal") : ""}
            ${section("Journal", journalMarkup(journal))}
        `;
    } catch (error) {
        showError(error.message);
    }
}

async function openHistoryTrade(id) {
    try {
        const trade = await api.getArmedTrade(id);
        const [event, candidate, journal] = await Promise.all([
            api.getFundingEvent(trade.fundingEventId),
            trade.signalCandidateId ? optionalRequest(() => api.getCandidate(trade.signalCandidateId)) : Promise.resolve(null),
            api.listArmedTradeJournal(id)
        ]);
        const attempts = await api.listOrderAttempts(id);

        nodes.drawerType.textContent = "Trade History";
        nodes.drawerTitle.textContent = trade.symbol ? `${trade.symbol} · ${trade.venue}` : `Trade #${trade.id}`;
        nodes.drawerContent.innerHTML = tradeHistoryDetailMarkup({ trade, event, candidate, journal, attempts });
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
        nodes.drawerType.textContent = "Venue Access";
        nodes.drawerTitle.textContent = venue.venue;
        nodes.drawerContent.innerHTML = `
            ${section("Venue profile", `
                <div class="meta-grid">
                    ${metaRow("Mode", escapeHtml(modeLabel(venue.configuredMode)))}
                    ${metaRow("Keys", credentialsBadge(venue))}
                    ${metaRow("Connection", formatConnectionBadge(venue.connectionStatus))}
                    ${metaRow("Connection note", escapeHtml(venue.connectionMessage ?? "Проверка ещё не запускалась."))}
                    ${metaRow("Metadata", venueHealthBadge(venue))}
                    ${metaRow("Metadata URL", escapeHtml(venue.metadataBaseUrl ?? "—"))}
                    ${metaRow("Contracts URL", escapeHtml(venue.contractsBaseUrl ?? "—"))}
                    ${metaRow("Active instruments", formatNumber(venue.activeInstrumentCount))}
                    ${metaRow("Last sync", formatInstant(venue.lastSyncedAt))}
                    ${metaRow("Last credential check", formatInstant(venue.lastCheckedAt))}
                </div>
            `)}
            ${section("Actions", `
                <div class="actions">
                    <button class="button secondary" type="button" data-action="check-venue" data-venue="${escapeHtml(venue.venue)}">Check keys</button>
                    <button class="button secondary" type="button" data-action="sync-venue" data-venue="${escapeHtml(venue.venue)}">Sync instruments</button>
                </div>
            `)}
            ${section("Request timings", timings.length ? timings.map((timing) => `
                <div class="meta-row">
                    <span class="meta-label">${escapeHtml(timing.operation)}</span>
                    <strong class="meta-value">${formatDurationMs(timing.averageDurationMs)}</strong>
                    <span class="meta-helper">${formatNumber(timing.requests)} req · ${formatNumber(timing.successes)} ok · ${formatNumber(timing.failures)} fail · last ${formatInstant(timing.lastOccurredAt)}</span>
                </div>
            `).join("") : emptyState("Timings пока пусты."))}
            ${section("Synced instruments", instruments.length ? instruments.map((instrument) => `
                <div class="meta-row">
                    <span class="meta-label">${escapeHtml(instrument.venueSymbol)}</span>
                    <strong class="meta-value">${escapeHtml(instrument.canonicalSymbol)}</strong>
                    <span class="meta-helper">${escapeHtml(instrument.status)} · step ${escapeHtml(instrument.qtyStep ?? "—")} · min qty ${escapeHtml(instrument.minOrderQty ?? "—")}</span>
                </div>
            `).join("") : emptyState("Instrument metadata пока нет.", "Сначала запусти sync по площадке."))}
        `;
    } catch (error) {
        showError(error.message);
    }
}

async function optionalRequest(loader) {
    try {
        return await loader();
    } catch (_error) {
        return null;
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
                showSuccess("Кандидат переведён в событие фандинга.");
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
            if (action === "delete-candidate") {
                if (!window.confirm("Удалить candidate и очистить связанный pipeline?")) {
                    return;
                }
                await api.deleteCandidate(form.dataset.id, data.get("deleteNote") || null);
                showSuccess("Candidate deleted and pipeline cleaned.");
                await refreshCurrentScreen();
                nodes.drawerType.textContent = "Inspector";
                nodes.drawerTitle.textContent = "Выбери объект";
                nodes.drawerContent.innerHTML = `<p class="muted">Открой signal, Funding Event, Prepared Trade или venue, чтобы посмотреть детали и следующее действие.</p>`;
                return;
            }
            if (action === "arm-event") {
                await api.armFundingEvent(form.dataset.id, {
                    notionalUsd: numberOrNull(data.get("notionalUsd")),
                    intendedSide: "SHORT",
                    plannedEntryAt: toIsoOrNull(data.get("plannedEntryAt")),
                    plannedExitAt: toIsoOrNull(data.get("plannedExitAt")),
                    entryAttemptCount: numberOrNull(data.get("entryAttemptCount")),
                    entrySpacingMs: numberOrNull(data.get("entrySpacingMs")),
                    manualLatencyAdjustmentMs: numberOrNull(data.get("manualLatencyAdjustmentMs")),
                    notes: data.get("notes") || null
                });
                showSuccess("Prepared Trade created.");
                await Promise.all([refreshCurrentScreen(), openEvent(form.dataset.id)]);
                return;
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
        return;
    }

    const checkButton = event.target.closest("[data-action='check-venue']");
    if (checkButton) {
        try {
            await api.checkVenueCredentials(checkButton.dataset.venue);
            showSuccess(`Credential check completed for ${checkButton.dataset.venue}.`);
            await Promise.all([refreshCurrentScreen(), openVenue(checkButton.dataset.venue)]);
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

function toLocalInputValue(value) {
    if (!value) {
        return "";
    }
    const date = new Date(value);
    const pad = (part) => String(part).padStart(2, "0");
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function offsetIso(value, seconds) {
    if (!value) {
        return null;
    }
    return new Date(new Date(value).getTime() + seconds * 1000).toISOString();
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
    nodes.drawerType.textContent = "Inspector";
    nodes.drawerTitle.textContent = "Выбери объект";
    nodes.drawerContent.innerHTML = `<p class="muted">Выбери объект из списка.</p>`;
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
