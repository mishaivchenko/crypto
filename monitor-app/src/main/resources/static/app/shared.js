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

const VENUE_ICONS = {
    gate: `<svg class="venue-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-label="Gate.io">
        <circle cx="12" cy="12" r="12" fill="#0052FF"/>
        <path d="M17 8.5H13.5V10.5H15.5V13.5H13.5V15.5H17V8.5Z" fill="white"/>
        <path d="M7 8.5H11.5V10.5H9V13.5H11.5V15.5H7V8.5Z" fill="white"/>
    </svg>`,
    bybit: `<svg class="venue-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-label="Bybit">
        <circle cx="12" cy="12" r="12" fill="#F7A600"/>
        <path d="M7 7.5H11.2C12.9 7.5 14 8.4 14 9.8C14 10.6 13.6 11.2 12.9 11.5C13.8 11.8 14.4 12.5 14.4 13.5C14.4 15 13.2 16 11.3 16H7V7.5ZM10.9 10.9C11.5 10.9 11.9 10.6 11.9 10.1C11.9 9.6 11.5 9.3 10.9 9.3H9V10.9H10.9ZM11.1 14.2C11.7 14.2 12.2 13.9 12.2 13.3C12.2 12.8 11.8 12.4 11.1 12.4H9V14.2H11.1Z" fill="black"/>
        <path d="M15.5 11.2L17 7.5H17.2L17 11.2V16H15.5V11.2Z" fill="black"/>
    </svg>`,
    okx: `<svg class="venue-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-label="OKX">
        <circle cx="12" cy="12" r="12" fill="#1A1A1A"/>
        <rect x="7" y="7" width="4" height="4" rx="0.8" fill="white"/>
        <rect x="13" y="7" width="4" height="4" rx="0.8" fill="white"/>
        <rect x="7" y="13" width="4" height="4" rx="0.8" fill="white"/>
        <rect x="13" y="13" width="4" height="4" rx="0.8" fill="white"/>
    </svg>`,
    kucoin: `<svg class="venue-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-label="KuCoin">
        <circle cx="12" cy="12" r="12" fill="#23AF91"/>
        <path d="M8 7.5V16.5L12.5 12L14.5 14L17 11.5L14.5 9L12.5 11L8 7.5Z" fill="white"/>
    </svg>`,
    bitget: `<svg class="venue-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-label="Bitget">
        <circle cx="12" cy="12" r="12" fill="#00F0FF" fill-opacity="0.15"/>
        <circle cx="12" cy="12" r="12" stroke="#00F0FF" stroke-width="1.5" fill="none"/>
        <path d="M8 9.5H13C14.4 9.5 15 10.2 15 11.2C15 11.9 14.6 12.4 14 12.6C14.8 12.9 15.4 13.5 15.4 14.4C15.4 15.5 14.5 16.2 13 16.2H8V9.5ZM12.6 12.1C13.2 12.1 13.5 11.8 13.5 11.3C13.5 10.8 13.2 10.5 12.6 10.5H9.5V12.1H12.6ZM12.8 15.2C13.4 15.2 13.8 14.9 13.8 14.3C13.8 13.7 13.4 13.4 12.8 13.4H9.5V15.2H12.8Z" fill="white"/>
    </svg>`
};

export function venueIcon(venueName) {
    if (!venueName) return "";
    const key = String(venueName).toLowerCase().replace(/[^a-z]/g, "");
    return VENUE_ICONS[key] ?? "";
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

export function candidateCard(candidate) {
    return `
        <article class="list-item signal-card">
            <header>
                <div>
                    <h3 class="item-title">${venueIcon(candidate.sourceVenue)}${escapeHtml(candidate.normalizedSymbol ?? candidate.rawSymbol)}</h3>
                    <p class="muted">${t("label_source")} ${escapeHtml(candidate.sourceVenue ?? sourceLabel(candidate.sourceType))} · ${t("label_raw")} ${escapeHtml(candidate.rawSymbol)}</p>
                </div>
                <div class="actions">
                    ${formatBadge("candidate", candidate.status)}
                    <button class="button secondary" type="button" data-open-candidate="${candidate.id}">${t("label_inspect")}</button>
                </div>
            </header>
            <div class="item-row">
                <span class="muted">${escapeHtml(candidateStateLine(candidate))}</span>
                <span class="muted">${formatInstant(candidate.detectedAt)} · ${formatRelative(candidate.detectedAt)}</span>
            </div>
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
