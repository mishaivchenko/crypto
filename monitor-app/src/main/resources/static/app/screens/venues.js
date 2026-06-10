import { emptyState, escapeHtml, formatInstant, venueIcon } from "../shared.js";
import { t } from "../../i18n.js";

// T-27: Coverage column cell
function coverageCell(pct) {
    if (pct == null) return '<span class="chip chip-muted">—</span>';
    if (pct >= 100) return `<span class="chip chip-good">${pct}%</span>`;
    if (pct >= 50)  return `<span class="chip chip-warning">${pct}%</span>`;
    return `<span class="chip chip-bad">${pct}%</span>`;
}

function staleChip(lastSyncedAt) {
    if (!lastSyncedAt) return '<span class="chip chip-warning">—</span>';
    const ageMs = Date.now() - new Date(lastSyncedAt).getTime();
    const stale = ageMs > 5 * 60 * 1000;
    const label = formatInstant(lastSyncedAt);
    if (stale) return `<span class="chip chip-warning">${escapeHtml(label)}</span>`;
    return `<span>${escapeHtml(label)}</span>`;
}

function modeBadge(venue) {
    const mode = venue.configuredMode ?? venue.mode ?? null;
    if (!mode) return "";
    const cls = mode === "testnet" ? "chip chip-muted" : "chip chip-good";
    return `<span class="${cls}">${escapeHtml(mode)}</span>`;
}

function sortVenues(venues, sortCol, sortDir) {
    if (!sortCol) return venues;
    const dir = sortDir === 'asc' ? 1 : -1;
    return [...venues].sort((a, b) => {
        if (sortCol === 'latency') {
            const pa = a.p50DurationMs ?? a.latencyP50Ms ?? null;
            const pb = b.p50DurationMs ?? b.latencyP50Ms ?? null;
            if (pa == null && pb == null) return 0;
            if (pa == null) return 1 * dir;
            if (pb == null) return -1 * dir;
            return (pa - pb) * dir;
        }
        if (sortCol === 'coverage') {
            const ca = a.enrichmentCoveragePct ?? -1;
            const cb = b.enrichmentCoveragePct ?? -1;
            return (ca - cb) * dir;
        }
        return 0;
    });
}

function latencySortLabel(sortCol, sortDir) {
    if (sortCol !== 'latency') return 'Задержка p50 ↕';
    return sortDir === 'asc' ? 'Задержка p50 ↑' : 'Задержка p50 ↓';
}

function coverageSortLabel(sortCol, sortDir) {
    if (sortCol !== 'coverage') return 'Покрытие ↕';
    return sortDir === 'asc' ? 'Покрытие ↑' : 'Покрытие ↓';
}

// T-26: Grid layout
export function venuesGridMarkup(venues, sortCol, sortDir) {
    if (!venues.length) {
        return emptyState(t("empty_venues"), t("empty_venues_detail"));
    }
    const sorted = sortVenues(venues, sortCol, sortDir);
    const rows = sorted.map(venue => {
        const p50raw = venue.p50DurationMs ?? venue.latencyP50Ms ?? null;
        const p50cell = p50raw != null ? `<span>${p50raw} ms</span>` : `<span class="chip chip-muted">—</span>`;
        return `
        <div class="venues-grid__row" data-open-venue="${escapeHtml(venue.venue)}">
            <span>${venueIcon(venue.venue)}${escapeHtml(venue.venue)} ${modeBadge(venue)}</span>
            <span>${venue.activeInstrumentCount != null ? escapeHtml(String(venue.activeInstrumentCount)) : "—"}</span>
            <span>${staleChip(venue.lastSyncedAt)}</span>
            <span>${p50cell}</span>
            <span><span class="chip chip-muted">—</span></span>
            <span>${coverageCell(venue.enrichmentCoveragePct ?? null)}</span>
        </div>`;
    }).join("");

    return `
    <div class="venues-grid">
        <div class="venues-grid__header">
            <span>Площадка</span>
            <span>Инструменты</span>
            <span>Последняя синхр.</span>
            <span class="sortable" data-col="latency">${latencySortLabel(sortCol, sortDir)}</span>
            <span>Liquidity Health</span>
            <span class="sortable" data-col="coverage">${coverageSortLabel(sortCol, sortDir)}</span>
        </div>
        ${rows}
    </div>`;
}

export function renderVenues({ nodes, venues, onOpenVenue }) {
    let sortCol = null, sortDir = 'asc';
    function render() {
        nodes.venuesList.innerHTML = venuesGridMarkup(venues, sortCol, sortDir);
        // wire row clicks
        nodes.venuesList.querySelectorAll('[data-open-venue]').forEach(el => {
            el.addEventListener('click', () => onOpenVenue(el.dataset.openVenue));
        });
        // wire sort header clicks
        nodes.venuesList.querySelectorAll('.sortable[data-col]').forEach(el => {
            el.addEventListener('click', () => {
                if (sortCol === el.dataset.col) sortDir = sortDir === 'asc' ? 'desc' : 'asc';
                else { sortCol = el.dataset.col; sortDir = 'asc'; }
                render();
            });
        });
    }
    render();
}
