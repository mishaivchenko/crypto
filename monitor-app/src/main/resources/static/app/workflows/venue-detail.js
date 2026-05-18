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
    openModal,
    section,
    venueHealthBadge,
    venueIcon
} from "../shared.js";
import { t } from "../../i18n.js";

function latencySection(venue, timings) {
    const timing = timings.find(t => t.operation === "order-submit") ?? null;
    const p50 = timing?.p50DurationMs != null ? `${timing.p50DurationMs} ms` : "—";
    const p95 = timing?.p95DurationMs != null ? `${timing.p95DurationMs} ms` : "—";
    const p99 = timing?.p99DurationMs != null ? `${timing.p99DurationMs} ms` : "—";
    const defaultMs = venue.defaultManualLatencyAdjustmentMs ?? "";
    return section(t("venue_latency_section"), `
        <div class="meta-grid">
            ${metaRow(t("venue_p50"), `<span id="venue-probe-result">${p50}</span>`)}
            ${metaRow(t("venue_p95"), p95)}
            ${metaRow(t("venue_p99"), p99)}
        </div>
        <p class="meta-helper">${t("venue_p_diagnostic_note")}</p>
        <div class="actions" style="margin-top:8px">
            <button class="button secondary" type="button"
                data-action="probe-venue-latency"
                data-venue="${escapeHtml(venue.venue)}">
                ${t("venue_probe_btn")}
            </button>
            <span id="venue-probe-inline" style="margin-left:8px;font-size:0.85em;color:var(--text-muted)"></span>
        </div>
        <form class="drawer-form" data-action="set-venue-default-latency" data-venue="${escapeHtml(venue.venue)}" style="margin-top:12px">
            <div class="drawer-form-row labeled-row">
                <label class="field">
                    <span>${t("venue_default_latency_override")}</span>
                    <input name="defaultManualLatencyAdjustmentMs" type="number" value="${escapeHtml(String(defaultMs))}" placeholder="e.g. 40">
                </label>
            </div>
            <div class="actions">
                <button class="button" type="submit">${t("venue_default_latency_save")}</button>
            </div>
        </form>
    `);
}

const PASSPHRASE_VENUES = new Set(["okx", "bitget", "kucoin"]);

export function buildVenueDrawerContent({ venue, instruments, timings }) {
    const needsPassphrase = PASSPHRASE_VENUES.has(venue.venue?.toLowerCase());
    const hasKeys = venue.credentialsConfigured;
    return `
        ${section(t("venue_profile"), `
            <div class="meta-grid">
                ${metaRow(t("venue_mode"), escapeHtml(modeLabel(venue.configuredMode)))}
                ${metaRow(t("venue_keys"), credentialsBadge(venue))}
                ${metaRow(t("venue_connection"), formatConnectionBadge(venue.connectionStatus))}
                ${metaRow(t("venue_connection_note"), escapeHtml(venue.connectionMessage ?? t("label_connection_not_checked")))}
                ${metaRow(t("venue_metadata"), venueHealthBadge(venue))}
                ${metaRow(t("venue_metadata_url"), escapeHtml(venue.metadataBaseUrl ?? "—"))}
                ${metaRow(t("venue_contracts_url"), escapeHtml(venue.contractsBaseUrl ?? "—"))}
                ${metaRow(t("venue_active_instruments"), formatNumber(venue.activeInstrumentCount))}
                ${metaRow(t("venue_last_sync"), formatInstant(venue.lastSyncedAt))}
                ${metaRow(t("venue_last_credential_check"), formatInstant(venue.lastCheckedAt))}
            </div>
        `)}
        ${section(t("venue_api_keys"), `
            <form class="drawer-form" data-action="upsert-credential" data-venue="${escapeHtml(venue.venue)}" data-mode="${escapeHtml(venue.configuredMode ?? "")}">
                <div class="drawer-form-row labeled-row">
                    <label class="field">
                        <span>${t("venue_api_key")}</span>
                        <input name="apiKey" type="password" autocomplete="off" placeholder="${t("venue_api_key_placeholder")}" required>
                    </label>
                    <label class="field">
                        <span>${t("venue_secret_key")}</span>
                        <input name="secretKey" type="password" autocomplete="off" placeholder="${t("venue_secret_key_placeholder")}" required>
                    </label>
                </div>
                ${needsPassphrase ? `
                <div class="drawer-form-row labeled-row">
                    <label class="field">
                        <span>${t("venue_passphrase")}</span>
                        <input name="passphrase" type="password" autocomplete="off" placeholder="${t("venue_passphrase")} (${escapeHtml(venue.venue)})">
                    </label>
                </div>` : ""}
                <div class="actions">
                    <button class="button" type="submit">${t("venue_save_keys")}</button>
                    ${hasKeys ? `<button class="button danger" type="button" data-action="delete-credential" data-venue="${escapeHtml(venue.venue)}" data-mode="${escapeHtml(venue.configuredMode ?? "")}">${t("venue_delete_keys")}</button>` : ""}
                </div>
            </form>
        `)}
        ${section(t("venue_actions"), `
            <div class="actions">
                <button class="button secondary" type="button" data-action="check-venue" data-venue="${escapeHtml(venue.venue)}">${t("venue_check_keys")}</button>
                <button class="button secondary" type="button" data-action="sync-venue" data-venue="${escapeHtml(venue.venue)}">${t("venue_sync_instruments")}</button>
            </div>
        `)}
        ${section(t("venue_request_timings"), timings.length ? timings.map((timing) => `
            <div class="meta-row">
                <span class="meta-label">${escapeHtml(timing.operation)}</span>
                <strong class="meta-value">${formatDurationMs(timing.averageDurationMs)}</strong>
                <span class="meta-helper">${formatNumber(timing.requests)} req · ${formatNumber(timing.successes)} ok · ${formatNumber(timing.failures)} fail · last ${formatInstant(timing.lastOccurredAt)}</span>
            </div>
        `).join("") : emptyState(t("empty_timings")))}
        ${latencySection(venue, timings)}
        ${section(t("venue_synced_instruments"), instruments.length ? instruments.map((instrument) => `
            <div class="meta-row">
                <span class="meta-label">${escapeHtml(instrument.venueSymbol)}</span>
                <strong class="meta-value">${escapeHtml(instrument.canonicalSymbol)}</strong>
                <span class="meta-helper">${escapeHtml(instrument.status)} · step ${escapeHtml(instrument.qtyStep ?? "—")} · min qty ${escapeHtml(instrument.minOrderQty ?? "—")}</span>
            </div>
        `).join("") : emptyState(t("empty_instruments"), t("empty_instruments_detail")))}
    `;
}

export async function openVenueDetail({ venueName, nodes, showError }) {
    try {
        const [venue, instruments, timings] = await Promise.all([
            api.getVenue(venueName),
            api.listVenueInstruments(venueName),
            api.listVenueTimings(venueName)
        ]);

        nodes.modalType.textContent = t("venue_modal_type");
        nodes.modalTitle.innerHTML = `${venueIcon(venue.venue)}${escapeHtml(venue.venue)}`;
        nodes.modalContent.innerHTML = buildVenueDrawerContent({ venue, instruments, timings });
        openModal(nodes);
    } catch (error) {
        showError(error.message);
    }
}
