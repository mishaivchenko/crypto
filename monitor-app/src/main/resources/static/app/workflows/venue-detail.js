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
import { renderLayerPipeline } from "../components/layer-pipeline.js";
import { renderEnrichmentTimestamp } from "../components/enrichment-timestamp.js";

// Kept for inline edit form reuse inside T-28 block 3
function latencyAdjustForm(venue) {
    const defaultMs = venue.defaultManualLatencyAdjustmentMs ?? "";
    return `
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
        </form>`;
}

// T-28: Three LayerBlock sections replacing the old latencySection call
function latencyLayerBlocks(venue, timings, venueTrades) {
    // --- Block 1: Latency Chain ---
    const submitTiming = timings.find(tm => tm.operation === "order-submit") ?? null;
    const p50 = submitTiming?.p50DurationMs ?? null;
    const p95 = submitTiming?.p95DurationMs ?? null;
    const p99 = submitTiming?.p99DurationMs ?? null;

    let latencyStatus;
    if (p50 == null) {
        latencyStatus = "missing";
    } else if (p50 < 400) {
        latencyStatus = "ok";
    } else if (p50 <= 600) {
        latencyStatus = "warn";
    } else {
        latencyStatus = "blocked";
    }

    const latencyChainContent = `
        <div class="meta-grid">
            ${metaRow(t("venue_p50"), `<span id="venue-probe-result">${p50 != null ? `${p50} ms` : "—"}</span>`)}
            ${metaRow(t("venue_p95"), p95 != null ? `${p95} ms` : "—")}
            ${metaRow(t("venue_p99"), p99 != null ? `${p99} ms` : "—")}
        </div>
        <p class="meta-helper">${t("venue_p_diagnostic_note")}</p>
        <div class="actions" style="margin-top:8px">
            <button class="button secondary" type="button"
                data-action="probe-venue-latency"
                data-venue="${escapeHtml(venue.venue)}">
                ${t("venue_probe_btn")}
            </button>
            <span id="venue-probe-inline" style="margin-left:8px;font-size:0.85em;color:var(--text-muted)"></span>
        </div>`;

    // --- Block 2: Warmup History ---
    let warmupStatus;
    let warmupContent;

    if (!venueTrades || venueTrades.length === 0) {
        warmupStatus = "missing";
        warmupContent = `<p class="muted" style="margin:4px 0">Нет данных о прогреве</p>`;
    } else {
        const anyFallback = venueTrades.some(tr => tr.warmupFallbackUsed);
        warmupStatus = anyFallback ? "warn" : "ok";
        const rows = venueTrades.map(tr => `
            <div class="meta-row">
                <span class="meta-label">${escapeHtml(tr.symbol ?? `Trade #${tr.id}`)}</span>
                <strong class="meta-value">${tr.warmupDoneAt ? renderEnrichmentTimestamp(tr.warmupDoneAt, null) : "—"}</strong>
                <span class="meta-helper">${tr.warmupFallbackUsed ? '<span class="chip chip-warning">fallback</span>' : '<span class="chip chip-good">ok</span>'}</span>
            </div>`).join("");
        warmupContent = `<div class="meta-grid">${rows}</div>`;
    }

    // --- Block 3: Default Adjustment ---
    const adjustMs = venue.defaultManualLatencyAdjustmentMs ?? 0;
    const adjustStatus = adjustMs === 0 ? "ok" : "warn";
    const adjustContent = latencyAdjustForm(venue);

    return renderLayerPipeline([
        {
            layerType: "latency",
            layerName: t("venue_latency_section"),
            decoratorName: "LatencyCalibrationService",
            timestamp: submitTiming?.lastOccurredAt ?? null,
            source: "order-submit",
            status: latencyStatus,
            collapsed: false,
            content: latencyChainContent
        },
        {
            layerType: "latency",
            layerName: "Warmup History",
            decoratorName: "WarmupProbeService",
            timestamp: venueTrades?.[0]?.warmupDoneAt ?? null,
            source: null,
            status: warmupStatus,
            collapsed: true,
            content: warmupContent
        },
        {
            layerType: "latency",
            layerName: t("venue_default_latency_override"),
            decoratorName: "ManualAdjustment",
            timestamp: null,
            source: null,
            status: adjustStatus,
            collapsed: true,
            content: adjustContent
        }
    ], { screen: 'venue-detail' });
}

const PASSPHRASE_VENUES = new Set(["okx", "bitget", "kucoin"]);

export function buildVenueDrawerContent({ venue, instruments, timings, trades }) {
    const needsPassphrase = PASSPHRASE_VENUES.has(venue.venue?.toLowerCase());
    const hasKeys = venue.credentialsConfigured;
    const venueTrades = (trades ?? []).slice(0, 3);
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
        ${latencyLayerBlocks(venue, timings, venueTrades)}
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
        const [venue, instruments, timings, allTrades] = await Promise.all([
            api.getVenue(venueName),
            api.listVenueInstruments(venueName),
            api.listVenueTimings(venueName),
            api.listArmedTrades({ includeHistorical: false }).catch(() => [])
        ]);

        const venueTrades = (allTrades ?? [])
            .filter(tr => tr.venue === venueName)
            .sort((a, b) => {
                const da = a.warmupDoneAt ? new Date(a.warmupDoneAt).getTime() : 0;
                const db = b.warmupDoneAt ? new Date(b.warmupDoneAt).getTime() : 0;
                return db - da;
            })
            .slice(0, 3);

        nodes.modalType.textContent = t("venue_modal_type");
        nodes.modalTitle.innerHTML = `${venueIcon(venue.venue)}${escapeHtml(venue.venue)}`;
        nodes.modalContent.innerHTML = buildVenueDrawerContent({ venue, instruments, timings, trades: venueTrades });
        openModal(nodes);
    } catch (error) {
        showError(error.message);
    }
}
