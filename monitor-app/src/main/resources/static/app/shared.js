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
        const assessVenue = candidate.suggestedVenue ?? candidate.sourceVenue ?? candidate.venueHints?.[0];
        const assessBtn = (assessVenue && symbol)
            ? `<button class="chip chip-btn" type="button" data-action="assess-card-liquidity" data-id="${candidate.id}" data-venue="${escapeHtml(assessVenue)}" data-symbol="${escapeHtml(symbol)}" title="${escapeHtml(t("liquidity_assess_button"))}">🔍</button>`
            : "";

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

        const aiReasoning = escapeHtml(ai.reasoning ?? "");
        const aiChips = `
            <div class="chip-row">
                <span class="chip chip-${aiTone}">
                    ${ai.recommendation === "GO" ? "✅" : ai.recommendation === "PASS" ? "🚫" : "👁️"}
                    ${escapeHtml(t(`ai_recommendation_${ai.recommendation}`) ?? ai.recommendation)}
                </span>
                <span class="chip chip-neutral">
                    🎯 ${Math.round(ai.confidence * 100)}%
                </span>
                ${aiReasoning ? `<span class="chip chip-info" title="${aiReasoning}">ℹ️ ${escapeHtml(ai.modelUsed ?? "AI")}</span>` : ""}
                <button class="chip chip-btn" type="button" data-action="analyze-candidate" data-id="${candidate.id}">🔄</button>
            </div>`;

        const liqScoreEmoji = liquidity.score === "EXCELLENT" ? "🟢" : liquidity.score === "GOOD" ? "🟡" : liquidity.score === "MEDIUM" ? "🟠" : "🔴";
        const liqChips = `
            <div class="chip-row">
                <span class="chip chip-${scoreTone}">${liqScoreEmoji} ${escapeHtml(t(`liquidity_score_${liquidity.score}`) ?? liquidity.score)}</span>
                ${liquidity.bestBid != null ? `<span class="chip chip-neutral" title="Best bid">B ${formatDecimal(liquidity.bestBid, 2)}</span>` : ""}
                ${liquidity.bestAsk != null ? `<span class="chip chip-neutral" title="Best ask">A ${formatDecimal(liquidity.bestAsk, 2)}</span>` : ""}
                ${liquidity.spreadBps != null ? `<span class="chip ${liquidity.spreadBps > 20 ? "chip-bad" : "chip-neutral"}" title="Spread">⇔ ${formatDecimal(liquidity.spreadBps, 1)} bps</span>` : ""}
                ${liquidity.entryBidDepthNotional != null ? `<span class="chip chip-neutral" title="Entry depth">📥 ${formatDecimal(liquidity.entryBidDepthNotional, 0)}$</span>` : ""}
                ${liquidity.exitAskDepthNotional != null ? `<span class="chip chip-neutral" title="Exit depth">📤 ${formatDecimal(liquidity.exitAskDepthNotional, 0)}$</span>` : ""}
                ${liquidity.recommendedMaxOrderNotional != null ? `<span class="chip chip-neutral" title="Max order">🔒 ${formatDecimal(liquidity.recommendedMaxOrderNotional, 0)}$</span>` : ""}
                ${assessBtn}
            </div>`;

        fullContent = `
            <div class="card-full-content">
                <div class="card-section-label">AI</div>
                ${aiChips}
                <div class="card-section-label">${t("liquidity_section_title")}</div>
                ${liqChips}
                ${actionsBlock}
            </div>`;
    }

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
            <div class="item-row">
                <span class="muted">${escapeHtml(candidateStateLine(candidate))}</span>
                <span class="muted">${formatInstant(candidate.detectedAt)} · ${formatRelative(candidate.detectedAt)}</span>
            </div>
            ${fullContent}
        </article>
    `;
}

export function eventCard(event) {
    return `
        <article class="list-item event-card">
            <header>
                <div>
                    <h3 class="item-title">${venueIcon(event.venue)}${escapeHtml(event.symbol)}</h3>
                    <p class="muted">${escapeHtml(event.venue)} · ${t("label_funding")} ${formatInstant(event.fundingTime)}</p>
                </div>
                <div class="actions">
                    ${formatBadge("event", event.status)}
                    <button class="button secondary" type="button" data-open-event="${event.id}">${t("label_inspect")}</button>
                </div>
            </header>
            <div class="item-row">
                <span class="muted">${t("card_signal")} ${event.signalCandidateId ?? t("label_manual")} · ${t("card_rate")} ${formatDecimal(event.fundingRatePct, 6)}</span>
                <span class="muted">${formatFundingCountdown(event.fundingTime)}</span>
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
            <div class="item-row">
                <span class="muted">${escapeHtml(sideLabel(trade.intendedSide))} · ${formatNumber(trade.entryAttemptCount ?? 1)} attempts · ${t("card_spacing")} ${formatDurationMs(trade.entrySpacingMs ?? 0)}</span>
                <span class="muted">entry ${formatInstant(trade.plannedEntryAt)} · exit ${formatInstant(trade.plannedExitAt)}</span>
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
    const pad = (part) => String(part).padStart(2, "0");
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
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
