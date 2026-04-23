import { api } from "../../api.js";
import {
    credentialsBadge,
    emptyState,
    escapeHtml,
    formatConnectionBadge,
    formatDurationMs,
    formatInstant,
    formatNumber,
    metaRow,
    modeLabel,
    section,
    venueHealthBadge
} from "../shared.js";

export function buildVenueDrawerContent({ venue, instruments, timings }) {
    return `
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
}

export async function openVenueDetail({ venueName, nodes, showError }) {
    try {
        const [venue, instruments, timings] = await Promise.all([
            api.getVenue(venueName),
            api.listVenueInstruments(venueName),
            api.listVenueTimings(venueName)
        ]);

        nodes.drawerType.textContent = "Venue Access";
        nodes.drawerTitle.textContent = venue.venue;
        nodes.drawerContent.innerHTML = buildVenueDrawerContent({ venue, instruments, timings });
    } catch (error) {
        showError(error.message);
    }
}
