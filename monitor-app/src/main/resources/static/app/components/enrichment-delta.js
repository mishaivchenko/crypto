// Score order: index 0 = worst, 4 = best
const SCORE_ORDER = ['UNTRADABLE', 'THIN', 'MEDIUM', 'GOOD', 'EXCELLENT'];

function _scoreIndex(score) {
    const idx = SCORE_ORDER.indexOf(score);
    return idx === -1 ? -1 : idx;
}

function _esc(s) {
    return String(s || '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}

function _fmtTimeDiff(fromIso, toIso) {
    if (!fromIso || !toIso) return '';
    const diffMs = +new Date(toIso) - +new Date(fromIso);
    if (isNaN(diffMs)) return '';
    const absMs = Math.abs(diffMs);
    const mins = Math.floor(absMs / 60000);
    const secs = Math.floor((absMs % 60000) / 1000);
    if (mins === 0) return secs + 'с';
    return mins + 'м' + (secs > 0 ? ' ' + secs + 'с' : '');
}

/**
 * Render enrichment delta between two LiquidityAssessment objects.
 *
 * @param {object} current   - {score, spreadBps, roundTripSafeNotional, sampledAt}
 * @param {object} baseline  - {score, spreadBps, roundTripSafeNotional, sampledAt} or null
 * @returns {string} HTML
 */
export function renderEnrichmentDelta(current, baseline) {
    if (!baseline) {
        return '<span class="chip chip-muted">Baseline недоступен</span>';
    }

    const sameScore = current.score === baseline.score;
    const sameSpread = current.spreadBps === baseline.spreadBps;
    const sameNotional = current.roundTripSafeNotional === baseline.roundTripSafeNotional;

    if (sameScore && sameSpread && sameNotional) {
        return '<span class="chip chip-muted">Ликвидность не изменилась</span>';
    }

    const timeDiff = _fmtTimeDiff(baseline.sampledAt, current.sampledAt);
    const timeLabel = timeDiff ? ' за ' + timeDiff : '';

    const parts = [];

    // spreadBps delta: positive = worse (red), negative = better (green)
    if (current.spreadBps != null && baseline.spreadBps != null) {
        const delta = current.spreadBps - baseline.spreadBps;
        if (delta !== 0) {
            const sign = delta > 0 ? '+' : '';
            const tone = delta > 0 ? 'bad' : 'good';
            const arrow = delta > 0 ? '↑' : '↓';
            parts.push(
                '<span class="chip chip-' + tone + '">'
                + _esc(sign + Math.round(delta) + ' bps ' + arrow + timeLabel)
                + '</span>'
            );
        }
    }

    // score delta: compare rank
    if (current.score && baseline.score && current.score !== baseline.score) {
        const curIdx = _scoreIndex(current.score);
        const baseIdx = _scoreIndex(baseline.score);
        // improved = higher index (better score) => green; worsened = lower index => red
        const improved = curIdx > baseIdx;
        const tone = improved ? 'good' : 'bad';
        parts.push(
            '<span class="chip chip-' + tone + '">'
            + _esc(baseline.score + ' → ' + current.score)
            + '</span>'
        );
    }

    // roundTripSafeNotional delta: decreased = bad (red), increased = good (green)
    if (current.roundTripSafeNotional != null && baseline.roundTripSafeNotional != null) {
        const delta = current.roundTripSafeNotional - baseline.roundTripSafeNotional;
        if (delta !== 0) {
            const sign = delta > 0 ? '+' : '−';
            const absDelta = Math.abs(Math.round(delta));
            const tone = delta >= 0 ? 'good' : 'bad';
            parts.push(
                '<span class="chip chip-' + tone + '">'
                + _esc(sign + '$' + absDelta)
                + '</span>'
            );
        }
    }

    if (parts.length === 0) {
        return '<span class="chip chip-muted">Ликвидность не изменилась</span>';
    }

    return '<div class="enrichment-delta chip-row">' + parts.join('') + '</div>';
}
