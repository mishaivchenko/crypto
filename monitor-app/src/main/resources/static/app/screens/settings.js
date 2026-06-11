import { api } from "../../api.js";
import { t, getLang, setLang } from "../../i18n.js";
import { escapeHtml } from "../shared.js";
import { renderAutoApproval } from "./auto-approval.js";
import { dashboardDevToolsMarkup } from "./dashboard.js";

// ─── AI Performance section ────────────────────────────────────────────────

function aiPerformanceMarkup(perf) {
    if (!perf || perf.totalTrades < 3) {
        return `<p class="helper-text muted">${t("ai_performance_no_data")}</p>`;
    }
    const rows = (perf.stats ?? []).map(s => {
        const winRate = s.winRate != null ? `${Math.round(s.winRate * 100)}%` : "—";
        const avgPnl = s.avgPnlUsd != null
            ? `${parseFloat(s.avgPnlUsd) >= 0 ? "+" : ""}$${parseFloat(s.avgPnlUsd).toFixed(2)}`
            : "—";
        const rec = t(`ai_recommendation_${s.recommendation}`) || s.recommendation;
        return `<tr><td>${escapeHtml(rec)}</td><td>${s.tradeCount}</td><td>${winRate}</td><td>${avgPnl}</td></tr>`;
    }).join("");
    return `<table class="perf-table">
        <thead><tr>
            <th></th>
            <th>${t("ai_performance_trades")}</th>
            <th>${t("ai_performance_win_rate")}</th>
            <th>${t("ai_performance_avg_pnl")}</th>
        </tr></thead>
        <tbody>${rows}</tbody>
    </table>`;
}

// ─── Global mode card ──────────────────────────────────────────────────────

function globalCardMarkup(currentLang) {
    return `
    <div class="meta-grid" style="margin-bottom:12px">
        <div class="meta-row">
            <span class="meta-label">Language</span>
            <span class="meta-value">
                <button class="button secondary settings-lang-btn ${currentLang === "en" ? "is-active" : ""}" data-lang="en" type="button">EN</button>
                <button class="button secondary settings-lang-btn ${currentLang === "ru" ? "is-active" : ""}" data-lang="ru" type="button">RU</button>
            </span>
        </div>
    </div>
    <form id="settings-global-mode-form" class="drawer-form">
        <div class="drawer-form-row labeled-row">
            <label class="field">
                <span>${t("topbar_access_mode")}</span>
                <select id="settings-global-mode-select" name="mode">
                    <option value="TESTNET">${t("topbar_testnet")}</option>
                    <option value="PRODUCTION">${t("topbar_production")}</option>
                </select>
            </label>
        </div>
        <div class="actions">
            <button class="button secondary" type="submit">${t("topbar_apply_mode")}</button>
        </div>
    </form>`;
}

// ─── Main render function ──────────────────────────────────────────────────

export async function renderSettings({
    nodes,
    state,
    showError,
    showSuccess,
    onRunEngineOnce,
    onUpdateEngineRuntime,
    onOpenDevTestRun,
    switchLang,
    loadGlobalMode
}) {
    const container = nodes.screens.settings;
    if (!container) return;

    const settingsContent = container.querySelector("#settings-content");
    if (!settingsContent) return;

    // Render static skeleton first so auto-approval has a target div (#dashboard-auto-approval) to render into
    const engineHtml = dashboardDevToolsMarkup(state.engineRuntime, state.engineRuntimeError);

    settingsContent.innerHTML = `
    <div style="display:grid;grid-template-columns:1fr 1fr;gap:16px">

        <!-- LEFT: Engine Control -->
        <div class="panel">
            <div class="panel-header">
                <h3>${t("dashboard_dev_tools")}</h3>
            </div>
            <div id="settings-engine-control">
                ${engineHtml}
            </div>
        </div>

        <!-- RIGHT: stacked cards -->
        <div style="display:flex;flex-direction:column;gap:16px">

            <!-- AI Advisor -->
            <div class="panel">
                <div class="panel-header">
                    <h3>${t("ai_toggle_label")}</h3>
                </div>
                <div style="padding:8px 0 4px">
                    <label class="toggle-row">
                        <input id="ai-toggle-checkbox-settings" type="checkbox">
                        <span id="ai-toggle-label-settings">${t("ai_toggle_label")}</span>
                    </label>
                </div>
                <div class="panel-header" style="margin-top:12px">
                    <h4>${t("ai_performance_title")}</h4>
                </div>
                <div id="settings-ai-performance">
                    <p class="muted">${t("loading_default")}</p>
                </div>
            </div>

            <!-- Pipeline Automation — renderAutoApproval looks for #dashboard-auto-approval -->
            <div class="panel">
                <div class="panel-header">
                    <h3>${t("auto_approval_title")}</h3>
                    <p class="muted">${t("auto_approval_subtitle")}</p>
                </div>
                <div id="dashboard-auto-approval"></div>
            </div>

            <!-- Global -->
            <div class="panel">
                <div class="panel-header">
                    <h3>Global</h3>
                </div>
                <div id="settings-global-card">
                    ${globalCardMarkup(getLang())}
                </div>
            </div>

        </div>
    </div>`;

    // ── Wire Engine Control buttons ────────────────────────────────────────
    const engineControlEl = container.querySelector("#settings-engine-control");

    const runOnceBtn = engineControlEl ? engineControlEl.querySelector("[data-action='run-engine-once']") : null;
    if (runOnceBtn && onRunEngineOnce) {
        runOnceBtn.addEventListener("click", onRunEngineOnce);
    }

    const devTestRunBtn = engineControlEl ? engineControlEl.querySelector("[data-action='open-dev-test-run']") : null;
    if (devTestRunBtn && onOpenDevTestRun) {
        devTestRunBtn.addEventListener("click", onOpenDevTestRun);
    }

    const runtimeForm = engineControlEl ? engineControlEl.querySelector("[data-action='update-engine-runtime']") : null;
    if (runtimeForm && onUpdateEngineRuntime) {
        runtimeForm.addEventListener("submit", onUpdateEngineRuntime);
    }

    // ── Wire AI toggle (sync state from topbar checkbox) ──────────────────
    const aiCheckbox = container.querySelector("#ai-toggle-checkbox-settings");
    const aiLabel = container.querySelector("#ai-toggle-label-settings");
    const topbarCheckbox = document.getElementById("ai-toggle-checkbox");
    if (aiCheckbox && topbarCheckbox) {
        aiCheckbox.checked = topbarCheckbox.checked;
        if (aiLabel) {
            aiLabel.textContent = topbarCheckbox.checked ? t("ai_toggle_enabled") : t("ai_toggle_disabled");
        }
    }
    if (aiCheckbox) {
        aiCheckbox.addEventListener("change", async () => {
            const enabled = aiCheckbox.checked;
            try {
                await api.setAiEnabled(enabled);
                if (topbarCheckbox) topbarCheckbox.checked = enabled;
                if (aiLabel) {
                    aiLabel.textContent = enabled ? t("ai_toggle_enabled") : t("ai_toggle_disabled");
                }
                showSuccess(enabled ? t("ai_toggle_enabled") : t("ai_toggle_disabled"));
            } catch (err) {
                showError(err.message);
                aiCheckbox.checked = !enabled;
            }
        });
    }

    // ── Load AI performance async ──────────────────────────────────────────
    const aiPerfEl = container.querySelector("#settings-ai-performance");
    api.getAiPerformance().then((perf) => {
        if (aiPerfEl) {
            aiPerfEl.innerHTML = aiPerformanceMarkup(perf);
        }
    }).catch((err) => {
        if (aiPerfEl) {
            aiPerfEl.innerHTML = `<p class="muted">${escapeHtml(err.message)}</p>`;
        }
    });

    // ── Load auto-approval (renders into #dashboard-auto-approval inside settings screen) ──
    renderAutoApproval({ nodes, showError, showSuccess });

    // ── Wire language buttons ──────────────────────────────────────────────
    container.querySelectorAll(".settings-lang-btn").forEach((btn) => {
        btn.addEventListener("click", () => {
            const lang = btn.dataset.lang;
            // switchLang() is a no-arg toggle — only call it when lang needs to change
            if (getLang() !== lang) {
                if (switchLang) {
                    switchLang();
                } else {
                    setLang(lang);
                }
            }
            // Update active state on both buttons
            container.querySelectorAll(".settings-lang-btn").forEach((b) => {
                b.classList.toggle("is-active", b.dataset.lang === lang);
            });
        });
    });

    // ── Wire Global mode form ──────────────────────────────────────────────
    const globalModeForm = container.querySelector("#settings-global-mode-form");
    const globalModeSelect = container.querySelector("#settings-global-mode-select");

    // Sync current mode into the select
    if (globalModeSelect) {
        if (state.overview) {
            globalModeSelect.value = String(state.overview.globalAccessMode ?? "TESTNET").toUpperCase();
        }
        if (loadGlobalMode) {
            loadGlobalMode().then((modeData) => {
                if (modeData && globalModeSelect) {
                    const modeValue = typeof modeData === "string" ? modeData : (modeData.mode ?? "TESTNET");
                    globalModeSelect.value = String(modeValue).toUpperCase();
                }
            }).catch(() => {
                // non-fatal
            });
        }
    }

    if (globalModeForm) {
        globalModeForm.addEventListener("submit", async (e) => {
            e.preventDefault();
            const mode = globalModeSelect ? globalModeSelect.value : "TESTNET";
            try {
                await api.setGlobalVenueMode(mode);
                showSuccess(`${t("app_mode_updated")} → ${mode}`);
            } catch (err) {
                showError(err.message);
            }
        });
    }
}
