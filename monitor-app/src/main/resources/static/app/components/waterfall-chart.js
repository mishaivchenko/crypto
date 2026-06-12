/**
 * Renders an order execution waterfall chart for a single venue.
 *
 * @param {Object} data - Waterfall response from GET /api/v1/order-attempts/waterfall
 * @returns {string} HTML string, or empty string if sampleSize === 0
 */
export function renderWaterfallChart(data) {
    if (!data || data.sampleSize === 0) {
        return "";
    }

    const venue = data.venue ?? "—";
    const sampleSize = data.sampleSize;
    const hasExchange = data.hasRealExchangeTimestamp;

    const avgHtml = _renderRow("avg", {
        wait: data.avgWaitMs,
        engine: data.avgEngineMs,
        http: data.avgHttpMs,
        exchange: hasExchange ? data.avgExchangeMs : null,
        total: data.totalAvgMs,
        hasExchange
    });

    const p95Total = _sumNullable(data.p95WaitMs, data.p95EngineMs, data.p95HttpMs, hasExchange ? data.p95ExchangeMs : null);
    const p95Html = _renderRow("p95", {
        wait: data.p95WaitMs,
        engine: data.p95EngineMs,
        http: data.p95HttpMs,
        exchange: hasExchange ? data.p95ExchangeMs : null,
        total: p95Total,
        hasExchange
    });

    const exchangeNote = hasExchange
        ? '<span style="color:#38a169;font-size:11px">* real exchange timestamp</span>'
        : '<span style="color:#888;font-size:11px">* exchange timestamp not available for this venue</span>';

    return `<div class="waterfall-venue" style="margin-bottom:16px">
  <div style="display:flex;align-items:baseline;gap:8px;margin-bottom:6px">
    <span style="font-weight:600;font-size:13px">${_esc(venue)}</span>
    <span style="color:#888;font-size:11px">(${sampleSize} samples)</span>
  </div>
  <div style="font-family:monospace;font-size:12px">
    ${avgHtml}
    ${p95Html}
  </div>
  <div style="margin-top:4px">${exchangeNote}</div>
</div>`;
}

function _renderRow(label, { wait, engine, http, exchange, total, hasExchange }) {
    if (total == null || total === 0) {
        return "";
    }

    const segments = [
        { ms: wait, color: "#555", name: "wait" },
        { ms: engine, color: "#3182ce", name: "engine" },
        { ms: http, color: "#d69e2e", name: "http" },
    ];
    if (hasExchange) {
        segments.push({ ms: exchange, color: "#38a169", name: "exchange" });
    }

    const barHtml = segments.map(({ ms, color }) => {
        if (ms == null || ms <= 0) return "";
        const pct = Math.max(1, Math.round((ms / total) * 100));
        return `<span style="display:inline-block;width:${pct}%;height:10px;background:${color};vertical-align:middle"></span>`;
    }).join("");

    const labelParts = segments
        .filter(s => s.ms != null && s.ms > 0)
        .map(s => `${s.name}:${s.ms}`)
        .join("  ");

    return `<div style="margin-bottom:4px">
  <span style="display:inline-block;width:32px;color:#aaa">${_esc(label)}</span>
  <span style="display:inline-block;width:200px;background:#222;vertical-align:middle;border-radius:2px;overflow:hidden">${barHtml}</span>
  <span style="color:#aaa;margin-left:6px">${total}ms total</span>
</div>
<div style="padding-left:36px;color:#888;font-size:11px;margin-bottom:4px">${_esc(labelParts)}</div>`;
}

function _sumNullable(...values) {
    let total = 0;
    let hasAny = false;
    for (const v of values) {
        if (v != null) {
            total += v;
            hasAny = true;
        }
    }
    return hasAny ? total : null;
}

function _esc(s) {
    return String(s ?? "")
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;");
}
