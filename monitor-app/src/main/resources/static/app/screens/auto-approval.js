import { api } from "../../api.js";

const AI_RECOMMENDATIONS = ["GO", "WATCH", "PASS"];
const LIQUIDITY_SCORES = ["EXCELLENT", "GOOD", "MEDIUM", "THIN", "UNTRADABLE"];
const VENUES = ["bybit", "gate", "bitget", "okx", "kucoin"];
const SIDES = ["SHORT", "LONG"];
const ACTIONS = ["AUTO_EXECUTE", "AUTO_REJECT"];
const MODES = ["BOTH", "TESTNET", "PRODUCTION"];

function statusBadge(enabled) {
    return `<span class="chip chip--${enabled ? "good" : "muted"}">${enabled ? "ON" : "OFF"}</span>`;
}

function actionBadge(action) {
    return `<span class="chip chip--${action === "AUTO_EXECUTE" ? "good" : "warning"}">${action}</span>`;
}

function ruleRow(rule) {
    const venues = rule.allowedVenues?.length ? rule.allowedVenues.join(", ") : "any";
    const ai = rule.allowedAiRecommendations?.length ? rule.allowedAiRecommendations.join(", ") : "any";
    const liq = rule.allowedLiquidityScores?.length ? rule.allowedLiquidityScores.join(", ") : "any";
    const rateRange = [
        rule.minFundingRatePct != null ? `≥${rule.minFundingRatePct}%` : null,
        rule.maxFundingRatePct != null ? `≤${rule.maxFundingRatePct}%` : null
    ].filter(Boolean).join(" ") || "any";

    return `
    <div class="list-item" data-rule-id="${rule.id}">
        <div class="list-item-header">
            <div class="list-item-title">
                <strong>${escHtml(rule.name)}</strong>
                ${statusBadge(rule.enabled)}
                ${actionBadge(rule.action)}
                <span class="chip chip--muted">priority ${rule.priority}</span>
                <span class="chip chip--muted">${rule.mode}</span>
            </div>
            <div class="list-item-actions">
                <button class="btn btn-sm" data-action="toggle-rule" data-rule-id="${rule.id}" data-enabled="${rule.enabled}">
                    ${rule.enabled ? "Disable" : "Enable"}
                </button>
                <button class="btn btn-sm btn--secondary" data-action="edit-rule" data-rule-id="${rule.id}">Edit</button>
                <button class="btn btn-sm btn--danger" data-action="delete-rule" data-rule-id="${rule.id}">Delete</button>
            </div>
        </div>
        <div class="chip-row">
            <span class="chip chip--muted">Rate: ${rateRange}</span>
            <span class="chip chip--muted">Venues: ${escHtml(venues)}</span>
            <span class="chip chip--muted">AI: ${escHtml(ai)}</span>
            ${rule.minAiConfidence != null ? `<span class="chip chip--muted">Conf ≥${rule.minAiConfidence}</span>` : ""}
            <span class="chip chip--muted">Liquidity: ${escHtml(liq)}</span>
            <span class="chip chip--muted">Notional: $${rule.defaultNotionalUsd} ${rule.defaultSide}</span>
        </div>
        ${rule.notes ? `<div class="list-item-note">${escHtml(rule.notes)}</div>` : ""}
    </div>`;
}

function multiCheckboxes(name, options, selected) {
    return options.map(o => `
        <label class="inline-label">
            <input type="checkbox" name="${name}" value="${o}" ${selected?.includes(o) ? "checked" : ""}> ${o}
        </label>`).join(" ");
}

function ruleForm(rule) {
    return `
    <form id="rule-form" class="form-stack">
        <div class="form-row">
            <label>Name *</label>
            <input type="text" name="name" value="${escHtml(rule?.name ?? "")}" required>
        </div>
        <div class="form-row">
            <label>Mode</label>
            <select name="mode">
                ${MODES.map(m => `<option value="${m}" ${(rule?.mode ?? "BOTH") === m ? "selected" : ""}>${m}</option>`).join("")}
            </select>
        </div>
        <div class="form-row">
            <label>Min funding rate %</label>
            <input type="number" step="0.0001" name="minFundingRatePct" value="${rule?.minFundingRatePct ?? ""}">
        </div>
        <div class="form-row">
            <label>Max funding rate %</label>
            <input type="number" step="0.0001" name="maxFundingRatePct" value="${rule?.maxFundingRatePct ?? ""}">
        </div>
        <div class="form-row">
            <label>Allowed venues (empty = any)</label>
            <div>${multiCheckboxes("allowedVenues", VENUES, rule?.allowedVenues)}</div>
        </div>
        <div class="form-row">
            <label>Allowed AI recommendations (empty = any)</label>
            <div>${multiCheckboxes("allowedAiRecommendations", AI_RECOMMENDATIONS, rule?.allowedAiRecommendations)}</div>
        </div>
        <div class="form-row">
            <label>Min AI confidence (0–1, empty = any)</label>
            <input type="number" step="0.01" min="0" max="1" name="minAiConfidence" value="${rule?.minAiConfidence ?? ""}">
        </div>
        <div class="form-row">
            <label>Allowed liquidity scores (empty = any)</label>
            <div>${multiCheckboxes("allowedLiquidityScores", LIQUIDITY_SCORES, rule?.allowedLiquidityScores)}</div>
        </div>
        <div class="form-row">
            <label>Default notional USD *</label>
            <input type="number" step="1" min="1" name="defaultNotionalUsd" value="${rule?.defaultNotionalUsd ?? 100}" required>
        </div>
        <div class="form-row">
            <label>Default side *</label>
            <select name="defaultSide">
                ${SIDES.map(s => `<option value="${s}" ${(rule?.defaultSide ?? "SHORT") === s ? "selected" : ""}>${s}</option>`).join("")}
            </select>
        </div>
        <div class="form-row">
            <label>Action *</label>
            <select name="action">
                ${ACTIONS.map(a => `<option value="${a}" ${(rule?.action ?? "AUTO_EXECUTE") === a ? "selected" : ""}>${a}</option>`).join("")}
            </select>
        </div>
        <div class="form-row">
            <label>Priority (lower = higher priority)</label>
            <input type="number" name="priority" value="${rule?.priority ?? 100}">
        </div>
        <div class="form-row">
            <label>Enabled</label>
            <input type="checkbox" name="enabled" ${(rule?.enabled ?? true) ? "checked" : ""}>
        </div>
        <div class="form-row">
            <label>Notes</label>
            <textarea name="notes" rows="2">${escHtml(rule?.notes ?? "")}</textarea>
        </div>
        <div class="form-actions">
            <button type="submit" class="btn">${rule ? "Save" : "Create Rule"}</button>
            <button type="button" id="cancel-form-btn" class="btn btn--secondary">Cancel</button>
        </div>
    </form>`;
}

function collectForm(form) {
    const fd = new FormData(form);
    const getNum = (k) => { const v = fd.get(k); return v === "" || v == null ? null : parseFloat(v); };
    const getList = (k) => fd.getAll(k).filter(Boolean);
    return {
        name: fd.get("name"),
        enabled: form.querySelector('[name="enabled"]').checked,
        mode: fd.get("mode"),
        minFundingRatePct: getNum("minFundingRatePct"),
        maxFundingRatePct: getNum("maxFundingRatePct"),
        allowedVenues: getList("allowedVenues"),
        allowedAiRecommendations: getList("allowedAiRecommendations"),
        minAiConfidence: getNum("minAiConfidence"),
        allowedLiquidityScores: getList("allowedLiquidityScores"),
        defaultNotionalUsd: getNum("defaultNotionalUsd"),
        defaultSide: fd.get("defaultSide"),
        action: fd.get("action"),
        priority: parseInt(fd.get("priority") || "100", 10),
        notes: fd.get("notes") || null
    };
}

export async function renderAutoApproval({ nodes, showError, showSuccess }) {
    const container = nodes.autoApprovalContent;
    container.innerHTML = `<div class="loading-state">Loading…</div>`;

    let status, rules;
    try {
        [status, rules] = await Promise.all([
            api.getAutoApprovalStatus(),
            api.listAutoApprovalRules()
        ]);
    } catch (e) {
        container.innerHTML = `<div class="empty-state"><p>Failed to load: ${escHtml(e.message)}</p></div>`;
        return;
    }

    function render(currentRules, currentStatus, formHtml) {
        container.innerHTML = `
        <div class="panel">
            <div class="panel-header">
                <h3>Global Toggle</h3>
            </div>
            <div class="panel-body">
                <p class="muted">Pipeline status: ${statusBadge(currentStatus.enabled)}
                   &nbsp;Active rules: <strong>${currentStatus.activeRulesCount}</strong></p>
                ${currentStatus.enabled
                    ? `<button class="btn btn--warning" id="global-disable-btn">Disable Auto-Approval</button>`
                    : `<button class="btn" id="global-enable-btn">Enable Auto-Approval</button>`}
                ${currentStatus.enabled
                    ? `<p class="hint">⚠ Pipeline is live — NORMALIZED candidates are processed automatically.</p>`
                    : ""}
            </div>
        </div>

        <div class="panel">
            <div class="panel-header">
                <h3>Rules <span class="chip chip--muted">${currentRules.length}</span></h3>
                <button class="btn btn-sm" id="add-rule-btn">+ Add Rule</button>
            </div>
            <div class="panel-body">
                ${formHtml ? `<div id="rule-form-container">${formHtml}</div>` : ""}
                ${currentRules.length === 0
                    ? `<p class="muted">No rules yet. Add a rule to start automating approvals.</p>`
                    : currentRules.map(ruleRow).join("")}
            </div>
        </div>`;

        wireEvents(currentStatus);
    }

    async function reload() {
        try {
            const [s, r] = await Promise.all([api.getAutoApprovalStatus(), api.listAutoApprovalRules()]);
            render(r, s, null);
        } catch (e) {
            showError(e.message);
        }
    }

    function wireEvents(currentStatus) {
        container.querySelector("#global-enable-btn")?.addEventListener("click", async () => {
            try { await api.enableAutoApproval(); showSuccess("Auto-approval enabled"); reload(); } catch (e) { showError(e.message); }
        });
        container.querySelector("#global-disable-btn")?.addEventListener("click", async () => {
            try { await api.disableAutoApproval(); showSuccess("Auto-approval disabled"); reload(); } catch (e) { showError(e.message); }
        });

        container.querySelector("#add-rule-btn")?.addEventListener("click", () => {
            render(rules, currentStatus, ruleForm(null));
            wireFormEvents(null);
        });

        container.querySelectorAll("[data-action='toggle-rule']").forEach(btn => {
            btn.addEventListener("click", async () => {
                const id = btn.dataset.ruleId;
                const isEnabled = btn.dataset.enabled === "true";
                try {
                    isEnabled ? await api.disableAutoApprovalRule(id) : await api.enableAutoApprovalRule(id);
                    reload();
                } catch (e) { showError(e.message); }
            });
        });

        container.querySelectorAll("[data-action='edit-rule']").forEach(btn => {
            btn.addEventListener("click", () => {
                const id = parseInt(btn.dataset.ruleId, 10);
                const rule = rules.find(r => r.id === id);
                if (!rule) return;
                render(rules, currentStatus, ruleForm(rule));
                wireFormEvents(id);
            });
        });

        container.querySelectorAll("[data-action='delete-rule']").forEach(btn => {
            btn.addEventListener("click", async () => {
                if (!confirm("Delete this rule?")) return;
                try { await api.deleteAutoApprovalRule(btn.dataset.ruleId); reload(); } catch (e) { showError(e.message); }
            });
        });
    }

    function wireFormEvents(editId) {
        container.querySelector("#cancel-form-btn")?.addEventListener("click", () => render(rules, status, null));
        container.querySelector("#rule-form")?.addEventListener("submit", async (e) => {
            e.preventDefault();
            const body = collectForm(e.target);
            try {
                if (editId != null) {
                    await api.updateAutoApprovalRule(editId, body);
                    showSuccess("Rule updated");
                } else {
                    await api.createAutoApprovalRule(body);
                    showSuccess("Rule created");
                }
                reload();
            } catch (err) { showError(err.message); }
        });
    }

    render(rules, status, null);
}

function escHtml(str) {
    if (str == null) return "";
    return String(str).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}
