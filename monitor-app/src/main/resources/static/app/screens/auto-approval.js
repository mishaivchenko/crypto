import { api } from "../../api.js";
import { escapeHtml } from "../shared.js";
import { t } from "../../i18n.js";

const AI_RECOMMENDATIONS = ["GO", "WATCH", "PASS"];
const LIQUIDITY_SCORES = ["EXCELLENT", "GOOD", "MEDIUM", "THIN", "UNTRADABLE"];
const VENUES = ["bybit", "gate", "bitget", "okx", "kucoin"];
const SIDES = ["SHORT", "LONG"];
const ACTIONS = ["AUTO_EXECUTE", "AUTO_REJECT"];
const MODES = ["BOTH", "TESTNET", "PRODUCTION"];

function statusBadge(enabled) {
    return `<span class="chip ${enabled ? "chip-good" : "chip-muted"}">${enabled ? "ON" : "OFF"}</span>`;
}

function actionBadge(action) {
    return `<span class="chip ${action === "AUTO_EXECUTE" ? "chip-good" : "chip-warning"}">${action}</span>`;
}

function ruleRow(rule) {
    const rateRange = [
        rule.minFundingRatePct != null ? `≥${rule.minFundingRatePct}%` : null,
        rule.maxFundingRatePct != null ? `≤${rule.maxFundingRatePct}%` : null
    ].filter(Boolean).join(" ") || "any";

    const venueChips = rule.allowedVenues?.length
        ? rule.allowedVenues.map(v => `<span class="chip chip-muted">${escapeHtml(v)}</span>`).join(" ")
        : `<span class="chip chip-muted">any</span>`;

    const liqChips = rule.allowedLiquidityScores?.length
        ? rule.allowedLiquidityScores.map(s => `<span class="chip chip-muted">${escapeHtml(s)}</span>`).join(" ")
        : `<span class="chip chip-muted">any</span>`;

    const aiChips = rule.allowedAiRecommendations?.length
        ? rule.allowedAiRecommendations.map(r => `<span class="chip chip-muted">${escapeHtml(r)}</span>`).join(" ")
        : `<span class="chip chip-muted">any</span>`;

    return `
    <div class="list-item" data-rule-id="${rule.id}">
        <div class="list-item-header">
            <div class="list-item-title">
                <strong>${escapeHtml(rule.name)}</strong>
                ${statusBadge(rule.enabled)}
                ${actionBadge(rule.action)}
                <span class="chip chip-muted">priority ${rule.priority}</span>
                <span class="chip chip-muted">${rule.mode}</span>
            </div>
            <div class="list-item-actions">
                <button class="button secondary" data-action="toggle-rule" data-rule-id="${rule.id}" data-enabled="${rule.enabled}">
                    ${rule.enabled ? "Disable" : "Enable"}
                </button>
                <button class="button secondary" data-action="edit-rule" data-rule-id="${rule.id}">Edit</button>
                <button class="button danger" data-action="delete-rule" data-rule-id="${rule.id}">Delete</button>
            </div>
        </div>
        <div style="margin-top:6px">
            <div style="border-left:3px solid var(--layer-base-border);padding:6px 8px;margin:4px 0;background:var(--layer-base-bg);border-radius:0 4px 4px 0">
                <strong style="font-size:0.75rem;display:block;margin-bottom:4px">${t("layer.base")}</strong>
                <div class="chip-row" style="margin:0">
                    ${venueChips}
                    <span class="chip chip-muted">Rate: ${rateRange}</span>
                    <span class="chip chip-muted">${rule.mode}</span>
                </div>
            </div>
            <div style="border-left:3px solid var(--layer-liquidity-border);padding:6px 8px;margin:4px 0;background:var(--layer-liquidity-bg);border-radius:0 4px 4px 0">
                <strong style="font-size:0.75rem;display:block;margin-bottom:4px">${t("layer.liquidity")}</strong>
                <div class="chip-row" style="margin:0">${liqChips}</div>
            </div>
            <div style="border-left:3px solid var(--layer-ai-border);padding:6px 8px;margin:4px 0;background:var(--layer-ai-bg);border-radius:0 4px 4px 0">
                <strong style="font-size:0.75rem;display:block;margin-bottom:4px">${t("layer.ai")}</strong>
                <div class="chip-row" style="margin:0">
                    ${aiChips}
                    ${rule.minAiConfidence != null ? `<span class="chip chip-muted">Conf ≥${rule.minAiConfidence}</span>` : ""}
                </div>
            </div>
            <div style="padding:6px 8px;margin:4px 0;border-radius:4px">
                <strong style="font-size:0.75rem;display:block;margin-bottom:4px">${t("auto_approval_section_action")}</strong>
                <div class="chip-row" style="margin:0">
                    ${actionBadge(rule.action)}
                    <span class="chip chip-muted">$${rule.defaultNotionalUsd}</span>
                    <span class="chip chip-muted">${rule.defaultSide}</span>
                    <span class="chip chip-muted">priority ${rule.priority}</span>
                </div>
            </div>
        </div>
        ${rule.notes ? `<div class="list-item-note">${escapeHtml(rule.notes)}</div>` : ""}
    </div>`;
}

function chipToggles(name, options, selected) {
    return options.map(o => `
        <label class="chip-toggle">
            <input type="checkbox" name="${name}" value="${o}" ${selected?.includes(o) ? "checked" : ""}>${o}
        </label>`).join(" ");
}

function ruleForm(rule) {
    return `
    <form id="rule-form" class="drawer-form">

        <div style="border-left:3px solid var(--layer-base-border);padding:6px 10px 10px;margin:8px 0;border-radius:0 4px 4px 0;background:var(--layer-base-bg)">
            <p class="field" style="margin:0 0 6px"><strong style="font-size:0.8rem">${t("layer.base")}</strong></p>
            <div class="field">
                <span>${t("auto_approval_form_venues")}</span>
                <div class="chip-row">${chipToggles("allowedVenues", VENUES, rule?.allowedVenues)}</div>
            </div>
            <div class="drawer-form-row labeled-row">
                <label class="field">
                    <span>${t("auto_approval_form_min_rate")}</span>
                    <input type="number" step="0.0001" name="minFundingRatePct" value="${rule?.minFundingRatePct ?? ""}">
                </label>
                <label class="field">
                    <span>${t("auto_approval_form_max_rate")}</span>
                    <input type="number" step="0.0001" name="maxFundingRatePct" value="${rule?.maxFundingRatePct ?? ""}">
                </label>
            </div>
            <label class="field">
                <span>${t("auto_approval_form_mode")}</span>
                <select name="mode">
                    ${MODES.map(m => `<option value="${m}" ${(rule?.mode ?? "BOTH") === m ? "selected" : ""}>${m}</option>`).join("")}
                </select>
            </label>
        </div>

        <div style="border-left:3px solid var(--layer-liquidity-border);padding:6px 10px 10px;margin:8px 0;border-radius:0 4px 4px 0;background:var(--layer-liquidity-bg)">
            <p class="field" style="margin:0 0 6px"><strong style="font-size:0.8rem">${t("layer.liquidity")}</strong></p>
            <div class="field">
                <span>${t("auto_approval_form_liquidity")}</span>
                <div class="chip-row">${chipToggles("allowedLiquidityScores", LIQUIDITY_SCORES, rule?.allowedLiquidityScores)}</div>
            </div>
        </div>

        <div style="border-left:3px solid var(--layer-ai-border);padding:6px 10px 10px;margin:8px 0;border-radius:0 4px 4px 0;background:var(--layer-ai-bg)">
            <p class="field" style="margin:0 0 6px"><strong style="font-size:0.8rem">${t("layer.ai")}</strong></p>
            <div class="field">
                <span>${t("auto_approval_form_ai_recs")}</span>
                <div class="chip-row">${chipToggles("allowedAiRecommendations", AI_RECOMMENDATIONS, rule?.allowedAiRecommendations)}</div>
            </div>
            <label class="field">
                <span>${t("auto_approval_form_ai_conf")}</span>
                <input type="number" step="0.01" min="0" max="1" name="minAiConfidence" value="${rule?.minAiConfidence ?? ""}">
            </label>
        </div>

        <div style="padding:6px 10px 10px;margin:8px 0;border-radius:4px;border:1px solid var(--border-color,#333)">
            <p class="field" style="margin:0 0 6px"><strong style="font-size:0.8rem">${t("auto_approval_section_action")}</strong></p>
            <label class="field">
                <span>${t("auto_approval_form_action")}</span>
                <select name="action">
                    ${ACTIONS.map(a => `<option value="${a}" ${(rule?.action ?? "AUTO_EXECUTE") === a ? "selected" : ""}>${a}</option>`).join("")}
                </select>
            </label>
            <div class="drawer-form-row labeled-row">
                <label class="field">
                    <span>${t("auto_approval_form_notional")}</span>
                    <input type="number" step="1" min="1" name="defaultNotionalUsd" value="${rule?.defaultNotionalUsd ?? 10}" required>
                </label>
                <label class="field">
                    <span>${t("auto_approval_form_side")}</span>
                    <select name="defaultSide">
                        ${SIDES.map(s => `<option value="${s}" ${(rule?.defaultSide ?? "SHORT") === s ? "selected" : ""}>${s}</option>`).join("")}
                    </select>
                </label>
            </div>
        </div>

        <div class="drawer-form-row labeled-row">
            <label class="field">
                <span>${t("auto_approval_form_name")}</span>
                <input type="text" name="name" value="${escapeHtml(rule?.name ?? "")}" required>
            </label>
            <label class="field">
                <span>${t("auto_approval_form_priority")}</span>
                <input type="number" name="priority" value="${rule?.priority ?? 100}">
            </label>
        </div>

        <div class="drawer-form-row labeled-row">
            <label class="toggle-row">
                <input type="checkbox" name="enabled" ${(rule?.enabled ?? true) ? "checked" : ""}>
                <span>${t("auto_approval_form_enabled")}</span>
            </label>
        </div>

        <label class="field">
            <span>${t("auto_approval_form_notes")}</span>
            <textarea name="notes" rows="2">${escapeHtml(rule?.notes ?? "")}</textarea>
        </label>

        <div class="actions">
            <button type="submit" class="button">${rule ? t("auto_approval_form_save") : t("auto_approval_form_create")}</button>
            <button type="button" id="cancel-form-btn" class="button secondary">${t("auto_approval_form_cancel")}</button>
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
    const container = document.getElementById("dashboard-auto-approval");
    if (!container) return;
    container.innerHTML = `<div class="loading-state">Loading…</div>`;

    let status, rules;
    try {
        [status, rules] = await Promise.all([
            api.getAutoApprovalStatus(),
            api.listAutoApprovalRules()
        ]);
    } catch (e) {
        container.innerHTML = `<div class="empty-state"><p>Failed to load: ${escapeHtml(e.message)}</p></div>`;
        return;
    }

    function render(currentRules, currentStatus, formHtml) {
        container.innerHTML = `
        <div class="action-card dev-tool-card">
            <div class="meta-grid">
                <div class="meta-row">
                    <span class="meta-label">Pipeline</span>
                    <span class="meta-value">${statusBadge(currentStatus.enabled)}</span>
                    <span class="meta-detail">Active rules: <strong>${currentStatus.activeRulesCount}</strong></span>
                </div>
            </div>
            <div class="actions" style="margin-top:8px">
                ${currentStatus.enabled
                    ? `<button class="button secondary" id="global-disable-btn">${t("auto_approval_disable")}</button>`
                    : `<button class="button" id="global-enable-btn">${t("auto_approval_enable")}</button>`}
            </div>
            ${currentStatus.enabled
                ? `<p class="helper-text" style="margin-top:6px">${t("auto_approval_live_warning")}</p>`
                : ""}
        </div>

        <div class="panel-header" style="margin-top:12px">
            <h4>${t("auto_approval_rules_header")} <span class="chip chip-muted">${currentRules.length}</span></h4>
            <button class="button secondary" id="add-rule-btn">${t("auto_approval_add_rule")}</button>
        </div>
        <div id="rule-form-container">${formHtml ?? ""}</div>
        <div id="rules-list">
            ${currentRules.length === 0
                ? `<p class="muted">${t("auto_approval_no_rules")}</p>`
                : currentRules.map(ruleRow).join("")}
        </div>`;

        wireEvents(currentStatus);
    }

    async function reload() {
        try {
            const [s, r] = await Promise.all([api.getAutoApprovalStatus(), api.listAutoApprovalRules()]);
            status = s;
            rules = r;
            render(rules, status, null);
        } catch (e) {
            showError(e.message);
        }
    }

    function wireEvents(currentStatus) {
        container.querySelector("#global-enable-btn")?.addEventListener("click", async () => {
            try { await api.enableAutoApproval(); showSuccess(t("auto_approval_enabled_msg")); reload(); } catch (e) { showError(e.message); }
        });
        container.querySelector("#global-disable-btn")?.addEventListener("click", async () => {
            try { await api.disableAutoApproval(); showSuccess(t("auto_approval_disabled_msg")); reload(); } catch (e) { showError(e.message); }
        });

        container.querySelector("#add-rule-btn")?.addEventListener("click", () => {
            container.querySelector("#rule-form-container").innerHTML = ruleForm(null);
            container.querySelector("#rules-list").innerHTML = "";
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
                    showSuccess(t("auto_approval_rule_updated"));
                } else {
                    await api.createAutoApprovalRule(body);
                    showSuccess(t("auto_approval_rule_created"));
                }
                reload();
            } catch (err) { showError(err.message); }
        });
    }

    render(rules, status, null);
}
