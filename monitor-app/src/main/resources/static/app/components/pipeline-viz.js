// T-22: PipelineViz — horizontal pipeline visualisation component

const LAYER_COLORS = {
    liquidity:  { bg: "rgba(49,130,206,0.12)", border: "rgba(49,130,206,0.40)", chip: "#2b6cb0" },
    latency:    { bg: "rgba(214,158,46,0.12)", border: "rgba(214,158,46,0.40)", chip: "#b7791f" },
    health:     { bg: "rgba(56,161,105,0.12)", border: "rgba(56,161,105,0.40)", chip: "#276749" },
    execution:  { bg: "rgba(128,90,213,0.12)", border: "rgba(128,90,213,0.40)", chip: "#6b46c1" },
    base:       { bg: "rgba(150,150,150,0.08)", border: "rgba(150,150,150,0.25)", chip: "#666" },
};

const STATUS_DOT = {
    ok:      { icon: "●", color: "var(--freshness-ok, #38a169)" },
    warn:    { icon: "▲", color: "var(--freshness-stale, #d69e2e)" },
    blocked: { icon: "✕", color: "var(--freshness-missing, #e53e3e)" },
    neutral: { icon: "○", color: "#888" },
    missing: { icon: "?", color: "#888" },
};

function _esc(s) {
    return String(s || "")
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;");
}

/**
 * Render a horizontal pipeline visualisation.
 *
 * @param {Array<{
 *   label: string,
 *   count: number,
 *   lastDecorator: string,
 *   decoratorType: string,
 *   status: 'ok'|'warn'|'blocked'|'neutral'|'missing',
 *   onClick: Function
 * }>} stages
 * @returns {string} HTML
 */
export function renderPipelineViz(stages) {
    if (!stages || stages.length === 0) {
        return '<div class="pipeline-viz pipeline-viz--empty" style="color:#888;font-style:italic;padding:8px">Нет данных pipeline</div>';
    }

    const stageEls = stages.map((stage, idx) => {
        const colors = LAYER_COLORS[stage.decoratorType] ?? LAYER_COLORS.base;
        const dot = STATUS_DOT[stage.status] ?? STATUS_DOT.neutral;
        const isLast = idx === stages.length - 1;

        const stageHtml = `<div class="pipeline-viz__stage" style="
                position:relative;
                display:inline-flex;
                flex-direction:column;
                align-items:center;
                gap:4px;
                padding:10px 14px 8px;
                border:1px solid ${colors.border};
                border-radius:8px;
                background:${colors.bg};
                min-width:90px;
                cursor:pointer;
            " role="button" tabindex="0" aria-label="${_esc(stage.label)}">
            <span style="position:absolute;top:6px;right:8px;font-size:11px;color:${dot.color}">${dot.icon}</span>
            <span style="font-size:11px;color:#aaa;font-weight:500">${_esc(stage.label)}</span>
            <strong style="font-size:22px;line-height:1.1;color:#e8e8e8">${_esc(String(stage.count ?? 0))}</strong>
            <span class="chip" style="font-size:10px;background:${colors.chip}22;color:${colors.chip};border:1px solid ${colors.chip}44;padding:1px 6px;border-radius:10px">${_esc(stage.lastDecorator)}</span>
        </div>`;

        const arrow = isLast ? "" : `<span style="color:#555;font-size:18px;align-self:center;padding:0 4px">→</span>`;

        return stageHtml + arrow;
    });

    const container = `<div class="pipeline-viz" style="display:flex;align-items:stretch;gap:4px;overflow-x:auto;padding:4px 0">${stageEls.join("")}</div>`;

    // Wire click handlers via data-attributes after insertion; return HTML + init script approach
    // Instead, expose a function to wire after DOM insertion
    return container;
}

/**
 * Wire click handlers on a rendered PipelineViz inside a DOM element.
 * Call this after inserting the HTML into the DOM.
 *
 * @param {HTMLElement} container
 * @param {Array<{onClick?: Function}>} stages - same stages array passed to renderPipelineViz
 */
export function wirePipelineVizClicks(container, stages) {
    const stageEls = container.querySelectorAll(".pipeline-viz__stage");
    stageEls.forEach((el, idx) => {
        const stage = stages[idx];
        if (stage && stage.onClick) {
            el.addEventListener("click", () => stage.onClick());
            el.addEventListener("keydown", (e) => {
                if (e.key === "Enter" || e.key === " ") {
                    e.preventDefault();
                    stage.onClick();
                }
            });
        }
    });
}
