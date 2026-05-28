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
    pipelineStageMarkup,
    section
} from "../ui.js";
import { t } from "../i18n.js";

export {
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
    pipelineStageMarkup,
    section
};

const VENUE_ICON_URLS = {
    gate: "https://coin-images.coingecko.com/markets/images/60/small/Frame_1.png?1747795534",
    bybit: "https://coin-images.coingecko.com/markets/images/698/small/bybit_spot.png?1706864649",
    okx: "https://coin-images.coingecko.com/markets/images/96/small/WeChat_Image_20220117220452.png?1706864283",
    kucoin: "https://coin-images.coingecko.com/markets/images/61/small/kucoin.png?1706864282",
    bitget: "https://coin-images.coingecko.com/markets/images/540/small/2023-07-25_21.47.43.jpg?1706864507"
};

export function venueIcon(venueName) {
    if (!venueName) return "";
    const key = String(venueName).toLowerCase().replace(/[^a-z]/g, "");
    const url = VENUE_ICON_URLS[key];
    return url ? `<img class="venue-icon" src="${url}" alt="${escapeHtml(venueName)}">` : "";
}

export function sourceLabel(value) {
    if (!value) {
        return "—";
    }
    const normalized = String(value).toLowerCase();
    if (normalized === "funding_api" || normalized === "funding-api") {
        return t("label_api_funding");
    }
    if (normalized === "manual") {
        return t("label_manual");
    }
    return String(value);
}

export function sideLabel(value) {
    if (!value) {
        return t("label_side_not_set");
    }
    return value === "LONG" ? t("label_long") : value === "SHORT" ? t("label_short") : value;
}

export function modeLabel(mode) {
    if (!mode) {
        return t("label_mode_not_set");
    }
    return mode === "testnet" ? t("label_testnet") : mode === "production" ? t("label_production") : String(mode);
}

export function credentialsBadge(venue) {
    if (venue.credentialsConfigured) {
        return formatBadge("venue", t("label_keys_ok"), "good");
    }
    return formatBadge("venue", venue.credentialsRequired ? t("label_no_keys") : t("label_keys_empty"), "warning");
}

export function venueHealthBadge(venue) {
    if (!venue.activeInstrumentCount) {
        return formatBadge("venue", t("label_no_instruments"), "warning");
    }
    if (!venue.lastSyncedAt) {
        return formatBadge("venue", t("label_no_sync"), "warning");
    }
    return formatBadge("venue", t("label_registry_ready"), "good");
}

export function connectionLine(venue) {
    const message = venue.connectionMessage ? escapeHtml(venue.connectionMessage) : t("label_connection_not_checked");
    const http = venue.lastConnectionHttpStatus ? ` · HTTP ${venue.lastConnectionHttpStatus}` : "";
    return `${message}${http}`;
}

export function summaryCard(title, value, detail, tone = "neutral", rawValue = false) {
    return `
        <article class="summary-card tone-${tone}">
            <span class="eyebrow">${escapeHtml(title)}</span>
            <strong>${rawValue ? escapeHtml(value) : formatNumber(value)}</strong>
            <p class="muted">${escapeHtml(detail)}</p>
        </article>
    `;
}

export function venueCard(venue) {
    return `
        <article class="list-item venue-card">
            <header>
                <div>
                    <h3 class="item-title">${venueIcon(venue.venue)}${escapeHtml(venue.venue)}</h3>
                    <p class="muted">${escapeHtml(modeLabel(venue.mode ?? venue.configuredMode))} · ${formatNumber(venue.activeInstrumentCount)} ${t("label_active_instruments")}</p>
                </div>
                <div class="actions">
                    ${credentialsBadge(venue)}
                    ${formatConnectionBadge(venue.connectionStatus)}
                    ${venueHealthBadge(venue)}
                    <button class="button secondary" type="button" data-open-venue="${escapeHtml(venue.venue)}">${t("label_open")}</button>
                </div>
            </header>
            <div class="item-row">
                <span class="muted">${connectionLine(venue)}</span>
                <span class="muted">${t("label_last_sync")} ${formatInstant(venue.lastSyncedAt)} · ${t("card_avg")} ${formatDurationMs(venue.averageRequestTimeMs)} · ${t("card_req")} ${formatNumber(venue.requests ?? 0)}</span>
            </div>
        </article>
    `;
}

export function candidateStateLine(candidate) {
    if (candidate.fundingEventId) {
        return `${t("candidate_state_linked")}${candidate.fundingEventId}`;
    }
    if (candidate.normalizationFailureReason) {
        return candidate.normalizationFailureReason;
    }
    if (candidate.venueHints?.length) {
        return `${t("label_venue_hints")} ${candidate.venueHints.join(", ")}`;
    }
    return t("candidate_state_review");
}

export function formatAiBadge(aiAdvice) {
    if (!aiAdvice) return "";
    const rec = aiAdvice.recommendation;
    const tone = rec === "GO" ? "good" : rec === "PASS" ? "bad" : "warning";
    const label = t(`ai_recommendation_${rec}`) ?? rec;
    const pct = Math.round(aiAdvice.confidence * 100);
    return `<span class="badge ai ${tone}">${escapeHtml(label)} ${pct}%</span>`;
}

export function candidateCard(candidate, { liquidity = null } = {}) {
    const ai = candidate.aiAdvice;
    const isFull = ai && liquidity;

    let fullContent = "";
    if (isFull) {
        const closed = candidate.status === "EVENT_CREATED" || candidate.status === "REJECTED" || candidate.status === "DELETED";

        const venue = candidate.suggestedVenue ?? candidate.sourceVenue ?? candidate.venueHints?.[0] ?? "";
        const symbol = candidate.normalizedSymbol ?? candidate.rawSymbol ?? "";
        const fundingTime = candidate.suggestedFundingTime ?? candidate.sourceFundingTime ?? null;
        const fundingRatePct = candidate.suggestedFundingRatePct ?? candidate.sourceFundingRatePct ?? null;
        const hasRequired = venue && symbol && fundingTime;

        const aiTone = ai.recommendation === "GO" ? "good" : ai.recommendation === "PASS" ? "bad" : "warning";
        const scoreTone = liquidity.score === "EXCELLENT" || liquidity.score === "GOOD" ? "good"
            : liquidity.score === "THIN" || liquidity.score === "UNTRADABLE" ? "bad" : "warning";
        const assessBtn = `<button class="chip chip-btn" type="button" data-action="assess-card-liquidity" data-id="${candidate.id}">${t("liquidity_assess_button")}</button>`;

        const actionsBlock = closed ? "" : `
            <div class="card-quick-actions">
                <button class="button approve" type="button"
                    data-action="quick-approve-candidate"
                    data-id="${candidate.id}"
                    data-venue="${escapeHtml(venue)}"
                    data-symbol="${escapeHtml(symbol)}"
                    data-funding-time="${escapeHtml(fundingTime ?? "")}"
                    data-funding-rate="${escapeHtml(String(fundingRatePct ?? ""))}"
                    ${hasRequired ? "" : "disabled"}>✓ ${t("signal_approve_button")}</button>
                <button class="button reject" type="button"
                    data-action="quick-reject-candidate"
                    data-id="${candidate.id}">✗ ${t("signal_reject_button")}</button>
                ${hasRequired
                    ? `<p class="signal-approve-hint">${escapeHtml(venue)} · ${escapeHtml(symbol)} · ${formatInstant(fundingTime)} · ${formatDecimal(fundingRatePct, 6)}%</p>`
                    : `<p class="signal-approve-warning">⚠ ${t("signal_approve_missing_data")}</p>`}
            </div>`;

        const reasoning = ai.reasoning ?? "";
        const aiRow = `
            <div class="chip-row">
                <span class="chip chip-${aiTone}">${escapeHtml(t(`ai_recommendation_${ai.recommendation}`) ?? ai.recommendation)}</span>
                <span class="chip chip-neutral">${Math.round(ai.confidence * 100)}%</span>
                ${reasoning ? `<details class="chip-details"><summary class="chip chip-muted">${escapeHtml(ai.modelUsed ?? "AI")}</summary><p class="chip-details-body">${escapeHtml(reasoning)}</p></details>` : ""}
                <button class="chip chip-btn" type="button" data-action="analyze-candidate" data-id="${candidate.id}">${t("ai_reanalyze")}</button>
            </div>`;

        const liqRow = `
            <div class="chip-row">
                <span class="chip chip-${scoreTone}">${escapeHtml(t(`liquidity_score_${liquidity.score}`) ?? liquidity.score)}</span>
                ${liquidity.bestBid != null ? `<span class="chip chip-muted" title="Bid / Ask">${formatDecimal(liquidity.bestBid, 4)} / ${liquidity.bestAsk != null ? formatDecimal(liquidity.bestAsk, 4) : "—"}</span>` : ""}
                ${liquidity.spreadBps != null ? `<span class="chip ${liquidity.spreadBps > 20 ? "chip-bad" : "chip-muted"}" title="Spread">${formatDecimal(liquidity.spreadBps, 1)} bps</span>` : ""}
                ${liquidity.entryBidDepthNotional != null ? `<span class="chip chip-muted" title="Entry / Exit depth">${formatDecimal(liquidity.entryBidDepthNotional, 0)} / ${liquidity.exitAskDepthNotional != null ? formatDecimal(liquidity.exitAskDepthNotional, 0) : "—"} USD</span>` : ""}
                ${liquidity.recommendedMaxOrderNotional != null ? `<span class="chip chip-muted" title="Max order">&le;${formatDecimal(liquidity.recommendedMaxOrderNotional, 0)} USD</span>` : ""}
                ${assessBtn}
            </div>`;

        fullContent = `
            <div class="card-full-content">
                ${aiRow}
                ${liqRow}
                ${actionsBlock}
            </div>`;
    }

    const cardFundingTime = candidate.suggestedFundingTime ?? candidate.sourceFundingTime ?? null;
    const cardRatePct = candidate.suggestedFundingRatePct ?? candidate.sourceFundingRatePct ?? null;
    const cardRateTone = cardRatePct == null ? "muted" : Number(cardRatePct) > 0.001 ? "good" : Number(cardRatePct) > 0.0005 ? "warning" : "muted";
    const cardRateChip = cardRatePct != null
        ? `<span class="chip chip-${cardRateTone}" title="${t("card_rate")}">${Number(cardRatePct) >= 0 ? "+" : ""}${formatDecimal(cardRatePct, 6)}%</span>`
        : "";
    const cardCountdownChip = cardFundingTime
        ? `<span class="chip chip-muted">${formatFundingCountdown(cardFundingTime)}</span>`
        : "";

    return `
        <article class="list-item signal-card" data-candidate-id="${candidate.id}">
            <header>
                <div>
                    <h3 class="item-title">${venueIcon(candidate.sourceVenue)}${escapeHtml(candidate.normalizedSymbol ?? candidate.rawSymbol)}</h3>
                    <p class="muted">${t("label_source")} ${escapeHtml(candidate.sourceVenue ?? sourceLabel(candidate.sourceType))} · ${t("label_raw")} ${escapeHtml(candidate.rawSymbol)}</p>
                </div>
                <div class="actions">
                    ${formatBadge("candidate", candidate.status)}
                    ${formatAiBadge(ai)}
                </div>
            </header>
            <div class="chip-row">
                ${cardRateChip}
                ${cardCountdownChip}
                <span class="chip chip-muted">${formatInstant(candidate.detectedAt)} · ${formatRelative(candidate.detectedAt)}</span>
            </div>
            <div class="item-row">
                <span class="muted">${escapeHtml(candidateStateLine(candidate))}</span>
            </div>
            ${fullContent}
        </article>
    `;
}

export function eventCard(event, { trade = null, outcome = null } = {}) {
    const ratePct = event.fundingRatePct != null ? Number(event.fundingRatePct) : null;
    const rateTone = ratePct == null ? "neutral" : ratePct < 0 ? "bad" : ratePct > 0.005 ? "good" : "neutral";
    const rateChip = ratePct != null
        ? `<span class="chip chip-${rateTone}" title="${t("card_rate")}">${ratePct >= 0 ? "+" : ""}${formatDecimal(ratePct, 6)}%</span>`
        : "";
    const countdown = formatFundingCountdown(event.fundingTime);

    let tradeChips = "";
    if (trade) {
        const effLat = trade.effectiveEntryLatencyMs ?? trade.measuredEntryLatencyMs;
        tradeChips = `
            ${formatBadge("trade", trade.state)}
            <span class="chip chip-muted" title="${t("trade_notional")}">${formatDecimal(trade.notionalUsd, 2)} USD</span>
            ${effLat != null ? `<span class="chip chip-muted" title="${t("trade_effective_trigger")}">${effLat}ms</span>` : ""}
            <span class="chip chip-muted" title="${t("trade_entry_attempts")}">${trade.entryAttemptCount ?? 1}x</span>
        `;
    }

    const net = outcome?.netPnlUsd != null ? Number(outcome.netPnlUsd) : null;
    const pnlChip = net != null
        ? `<span class="chip chip-${net >= 0 ? "good" : "bad"}" title="Net PnL">${net >= 0 ? "+" : ""}${formatDecimal(net, 4)} USD</span>`
        : "";

    return `
        <article class="list-item event-card" data-event-id="${event.id}">
            <header>
                <div>
                    <h3 class="item-title">${venueIcon(event.venue)}${escapeHtml(event.symbol)}</h3>
                    <p class="muted">${escapeHtml(event.venue)} · ${formatInstant(event.fundingTime)}</p>
                </div>
                <div class="actions">
                    ${formatBadge("event", event.status)}
                    <button class="button secondary" type="button" data-open-event="${event.id}">${t("label_inspect")}</button>
                </div>
            </header>
            <div class="chip-row">
                ${rateChip}
                <span class="chip chip-muted" title="${t("card_signal")}">#${event.signalCandidateId ?? t("label_manual")}</span>
                <span class="chip chip-muted">${countdown}</span>
                ${tradeChips}
                ${pnlChip}
            </div>
        </article>
    `;
}

export function formatPnlBadge(outcome) {
    if (!outcome || outcome.netPnlUsd == null) {
        return "";
    }
    const net = Number(outcome.netPnlUsd);
    const sign = net >= 0 ? "+" : "";
    const tone = net >= 0 ? "good" : "bad";
    const fees = outcome.feesUsd != null ? ` (${t("card_fees")} ${formatDecimal(outcome.feesUsd, 4)})` : "";
    return formatBadge("outcome", `${sign}${formatDecimal(net, 4)} USD${fees}`, tone);
}

export function tradeCard(trade, outcome = null) {
    const net = outcome?.netPnlUsd != null ? Number(outcome.netPnlUsd) : null;
    const pnlChip = net != null
        ? `<span class="chip chip-${net >= 0 ? "good" : "bad"}" title="Net PnL">${net >= 0 ? "+" : ""}${formatDecimal(net, 4)} USD</span>`
        : "";
    const effLat = trade.effectiveEntryLatencyMs ?? trade.measuredEntryLatencyMs;

    const ratePct = trade.fundingRatePct != null ? Number(trade.fundingRatePct) : null;
    const rateTone = ratePct == null ? "muted" : ratePct > 0.001 ? "good" : ratePct > 0.0005 ? "warning" : "muted";
    const rateChip = ratePct != null
        ? `<span class="chip chip-${rateTone}" title="${t("card_rate")}">${ratePct >= 0 ? "+" : ""}${formatDecimal(ratePct, 6)}%</span>`
        : "";

    const countdown = formatFundingCountdown(trade.fundingTime);
    const countdownChip = `<span class="chip chip-muted">${countdown}</span>`;

    return `
        <article class="list-item trade-card">
            <header>
                <div>
                    <h3 class="item-title">${venueIcon(trade.venue)}${escapeHtml(trade.symbol ?? `${t("card_trade_prefix")}${trade.id}`)}</h3>
                    <p class="muted">${escapeHtml(trade.venue ?? t("card_venue_not_set"))} · ${t("card_event_prefix")} ${trade.fundingEventId} · ${formatDecimal(trade.notionalUsd, 2)} USD</p>
                </div>
                <div class="actions">
                    ${formatBadge("trade", trade.state)}
                    ${trade.mode === "testnet" ? formatBadge("venue", t("label_testnet"), "info") : ""}
                    ${formatPnlBadge(outcome)}
                    <button class="button secondary" type="button" data-open-trade="${trade.id}">${t("label_inspect")}</button>
                </div>
            </header>
            <div class="chip-row">
                ${rateChip}
                ${countdownChip}
                <span class="chip chip-muted" title="${t("trade_side")}">${escapeHtml(sideLabel(trade.intendedSide))}</span>
                <span class="chip chip-muted" title="${t("trade_entry_attempts")}">${formatNumber(trade.entryAttemptCount ?? 1)}x · ${formatDurationMs(trade.entrySpacingMs ?? 0)}</span>
                ${effLat != null ? `<span class="chip chip-muted" title="${t("trade_effective_trigger")}">${effLat}ms</span>` : ""}
                <span class="chip chip-muted">entry ${formatInstant(trade.plannedEntryAt)}</span>
                ${pnlChip}
            </div>
        </article>
    `;
}

export function wireOpenButtons(container, selector, handler) {
    container.querySelectorAll(selector).forEach((button) => {
        button.addEventListener("click", () => handler(button.dataset.openCandidate || button.dataset.openEvent || button.dataset.openTrade || button.dataset.openVenue));
    });
}

export function groupAttemptsByTrade(attempts) {
    return (attempts ?? []).reduce((acc, attempt) => {
        const key = String(attempt.armedTradeId ?? "");
        if (!acc[key]) {
            acc[key] = [];
        }
        acc[key].push(attempt);
        return acc;
    }, {});
}

export async function optionalRequest(loader) {
    try {
        return await loader();
    } catch (_error) {
        return null;
    }
}

export function toIsoOrNull(value) {
    if (!value) {
        return null;
    }
    return new Date(value).toISOString();
}

export function toLocalInputValue(value) {
    if (!value) {
        return "";
    }
    const date = new Date(value);
    const pad2 = (n) => String(n).padStart(2, "0");
    const pad3 = (n) => String(n).padStart(3, "0");
    return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}T${pad2(date.getHours())}:${pad2(date.getMinutes())}:${pad2(date.getSeconds())}.${pad3(date.getMilliseconds())}`;
}

export function offsetIso(value, seconds) {
    if (!value) {
        return null;
    }
    return new Date(new Date(value).getTime() + seconds * 1000).toISOString();
}

export function numberOrNull(value) {
    if (value === null || value === undefined || value === "") {
        return null;
    }
    const parsed = Number(value);
    return Number.isNaN(parsed) ? null : parsed;
}

export function resetDrawer(nodes) {
    closeModal(nodes);
}

export function openModal(nodes) {
    nodes.inspectorModal.hidden = false;
    document.body.style.overflow = "hidden";
}

export function closeModal(nodes) {
    nodes.inspectorModal.hidden = true;
    document.body.style.overflow = "";
    nodes.modalType.textContent = t("inspector_type");
    nodes.modalTitle.textContent = "—";
    nodes.modalContent.innerHTML = "";
}
