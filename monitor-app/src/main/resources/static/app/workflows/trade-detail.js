import { api } from "../../api.js";
import {
    emptyState,
    escapeHtml,
    formatBadge,
    formatDecimal,
    formatDurationMs,
    formatFundingCountdown,
    formatInstant,
    formatNumber,
    formatSignedMs,
    journalMarkup,
    metaRow,
    modeLabel,
    openModal,
    pipelineStageMarkup,
    section,
    sideLabel,
    toLocalInputValue
} from "../shared.js";
import { buildDeleteCandidateSection } from "./pipeline.js";

const CANCELLABLE_STATES = new Set(["ARMED", "ENTRY_PENDING", "ENTRY_ATTEMPTED"]);
const CLOSEABLE_STATES = new Set(["OPEN", "EXIT_PENDING"]);

function buildCancelSection(trade) {
    if (CLOSEABLE_STATES.has(trade.state)) {
        return section("Close position", `
            <p class="muted">Отправит exit-ордер через engine. Позиция на бирже будет закрыта.</p>
            <button class="button danger" type="button" data-close-trade="${trade.id}">Закрыть позицию</button>
        `);
    }
    if (!CANCELLABLE_STATES.has(trade.state)) {
        return "";
    }
    return section("Cancel trade", `
        <p class="muted">Переведёт сделку в CANCELLED. Открытые позиции на бирже не закрываются — только запись в системе.</p>
        <button class="button danger" type="button" data-cancel-trade="${trade.id}">Отменить сделку</button>
    `);
}

export function buildTradeDrawerContent({ trade, journal, attempts }) {
    return `
        ${pipelineStageMarkup("trade")}
        ${section("Trade parameters", `
            <div class="meta-grid">
                ${metaRow("Статус", formatBadge("trade", trade.state))}
                ${metaRow("Funding Event", `#${trade.fundingEventId}`)}
                ${metaRow("Source signal", trade.signalCandidateId ? `#${trade.signalCandidateId}` : "manual")}
                ${metaRow("Venue", escapeHtml(trade.venue ?? "—"))}
                ${metaRow("Mode", trade.mode ? formatBadge("venue", modeLabel(trade.mode)) : "—")}
                ${metaRow("Instrument", escapeHtml(trade.symbol ?? "—"))}
                ${metaRow("Funding time", formatInstant(trade.fundingTime), formatFundingCountdown(trade.fundingTime))}
                ${metaRow("Notional", `${formatDecimal(trade.notionalUsd, 2)} USD`)}
                ${metaRow("Side", escapeHtml(sideLabel(trade.intendedSide)))}
                ${metaRow("Planned entry", formatInstant(trade.plannedEntryAt))}
                ${metaRow("Planned exit", formatInstant(trade.plannedExitAt))}
                ${metaRow("Entry attempts", formatNumber(trade.entryAttemptCount ?? 1), `spacing ${formatDurationMs(trade.entrySpacingMs ?? 0)}`)}
                ${trade.stopLossUsd != null ? metaRow("Stop Loss", `${formatDecimal(trade.stopLossUsd, 2)} USD`) : ""}
                ${trade.takeProfitUsd != null ? metaRow("Take Profit", `${formatDecimal(trade.takeProfitUsd, 2)} USD`) : ""}
                ${metaRow("Note", escapeHtml(trade.notes ?? "—"))}
            </div>
            <details class="technical-details">
                <summary>Latency details</summary>
                <div class="meta-grid">
                    ${metaRow("Measured latency", formatDurationMs(trade.measuredEntryLatencyMs))}
                    ${metaRow("Manual latency adj", formatSignedMs(trade.manualLatencyAdjustmentMs ?? 0))}
                    ${metaRow("Effective trigger lead", formatDurationMs(trade.effectiveEntryLatencyMs ?? 0))}
                    ${metaRow("Armed at", formatInstant(trade.armedAt))}
                    ${metaRow("Entry lead", formatDurationMs(trade.entryLeadMs))}
                    ${metaRow("Exit lead", formatDurationMs(trade.exitLeadMs))}
                    ${metaRow("Arm source", escapeHtml(trade.armSource ?? "—"))}
                </div>
            </details>
        `)}
        ${section("Execution attempts", attempts.length ? attempts.map((attempt) => `
            <div class="meta-row">
                <span class="meta-label">#${escapeHtml(attempt.attemptNumber ?? "—")} · ${escapeHtml(attempt.status)}</span>
                <strong class="meta-value">${escapeHtml(attempt.symbol)} ${escapeHtml(attempt.side)}</strong>
                <span class="meta-helper">${escapeHtml(attempt.failureReason ?? "Без ошибки")} · trigger ${formatInstant(attempt.triggerAt)} · recorded ${formatInstant(attempt.createdAt)}</span>
            </div>
        `).join("") : emptyState("Execution attempts пока нет.", "Запусти engine run-once, чтобы увидеть FAILED/SUBMITTED попытки."))}
        ${buildCancelSection(trade)}
        ${trade.state === "ARMED" ? section("Edit Trade", `
            <details class="technical-details">
                <summary>Изменить параметры</summary>
                <form class="drawer-form" data-action="edit-trade" data-id="${trade.id}">
                    <fieldset class="form-group">
                        <legend>Entry Window</legend>
                        <div class="drawer-form-row labeled-row">
                            <label class="field">
                                <span>Notional, USD</span>
                                <input name="notionalUsd" type="number" step="0.01" value="${escapeHtml(String(trade.notionalUsd ?? ""))}">
                            </label>
                            <label class="field">
                                <span>Entry attempts</span>
                                <input name="entryAttemptCount" type="number" min="1" max="25" step="1" value="${escapeHtml(String(trade.entryAttemptCount ?? 1))}">
                            </label>
                        </div>
                        <div class="drawer-form-row labeled-row">
                            <label class="field">
                                <span>Planned entry</span>
                                <input name="plannedEntryAt" type="datetime-local" value="${escapeHtml(toLocalInputValue(trade.plannedEntryAt) ?? "")}">
                            </label>
                            <label class="field">
                                <span>Spacing, ms</span>
                                <input name="entrySpacingMs" type="number" min="0" step="1" value="${escapeHtml(String(trade.entrySpacingMs ?? 0))}">
                            </label>
                        </div>
                        <label class="field">
                            <span>Manual latency adj, ms</span>
                            <input name="manualLatencyAdjustmentMs" type="number" min="-60000" max="60000" step="1" value="${escapeHtml(String(trade.manualLatencyAdjustmentMs ?? 0))}">
                        </label>
                    </fieldset>
                    <fieldset class="form-group">
                        <legend>Exit Window</legend>
                        <label class="field">
                            <span>Planned exit</span>
                            <input name="plannedExitAt" type="datetime-local" value="${escapeHtml(toLocalInputValue(trade.plannedExitAt) ?? "")}">
                        </label>
                    </fieldset>
                    <fieldset class="form-group">
                        <legend>Risk Management</legend>
                        <div class="drawer-form-row labeled-row">
                            <label class="field">
                                <span>Stop Loss, USD</span>
                                <input name="stopLossUsd" type="number" step="0.01" min="0" placeholder="e.g. 50.00" value="${escapeHtml(trade.stopLossUsd != null ? String(trade.stopLossUsd) : "")}">
                            </label>
                            <label class="field">
                                <span>Take Profit, USD</span>
                                <input name="takeProfitUsd" type="number" step="0.01" min="0" placeholder="e.g. 50.00" value="${escapeHtml(trade.takeProfitUsd != null ? String(trade.takeProfitUsd) : "")}">
                            </label>
                        </div>
                    </fieldset>
                    <label class="field">
                        <span>Note</span>
                        <textarea name="notes">${escapeHtml(trade.notes ?? "")}</textarea>
                    </label>
                    <div class="actions">
                        <button class="button" type="submit">Сохранить изменения</button>
                    </div>
                </form>
            </details>
        `) : ""}
        ${trade.signalCandidateId ? buildDeleteCandidateSection({ id: trade.signalCandidateId }, "Delete source signal") : ""}
        ${section("Journal", journalMarkup(journal))}
    `;
}

export async function openTradeDetail({ id, nodes, showError, onRefresh }) {
    try {
        const [trade, journal, attempts] = await Promise.all([
            api.getArmedTrade(id),
            api.listArmedTradeJournal(id),
            api.listOrderAttempts(id)
        ]);

        nodes.modalType.textContent = "Prepared Trade";
        nodes.modalTitle.textContent = trade.symbol ? `${trade.symbol} · ${trade.venue}` : `Сделка #${trade.id}`;
        nodes.modalContent.innerHTML = buildTradeDrawerContent({ trade, journal, attempts });
        openModal(nodes);

        const cancelBtn = nodes.modalContent.querySelector("[data-cancel-trade]");
        if (cancelBtn) {
            cancelBtn.addEventListener("click", async () => {
                cancelBtn.disabled = true;
                cancelBtn.textContent = "Отменяем…";
                try {
                    await api.cancelArmedTrade(trade.id);
                    if (onRefresh) onRefresh();
                    await openTradeDetail({ id, nodes, showError, onRefresh });
                } catch (err) {
                    showError(err.message);
                    cancelBtn.disabled = false;
                    cancelBtn.textContent = "Отменить сделку";
                }
            });
        }

        const closeBtn = nodes.modalContent.querySelector("[data-close-trade]");
        if (closeBtn) {
            closeBtn.addEventListener("click", async () => {
                closeBtn.disabled = true;
                closeBtn.textContent = "Закрываем…";
                try {
                    await api.closeArmedTrade(trade.id);
                    if (onRefresh) onRefresh();
                    await openTradeDetail({ id, nodes, showError, onRefresh });
                } catch (err) {
                    showError(err.message);
                    closeBtn.disabled = false;
                    closeBtn.textContent = "Закрыть позицию";
                }
            });
        }

        const editForm = nodes.modalContent.querySelector("[data-action='edit-trade']");
        if (editForm) {
            editForm.addEventListener("submit", async (e) => {
                e.preventDefault();
                const data = new FormData(editForm);
                const submitBtn = editForm.querySelector("[type='submit']");
                submitBtn.disabled = true;
                submitBtn.textContent = "Сохраняем…";
                try {
                    const toInstant = (v) => v ? new Date(v).toISOString() : null;
                    const toNum = (v) => v !== "" && v != null ? Number(v) : null;
                    await api.updateArmedTrade(trade.id, {
                        notionalUsd: toNum(data.get("notionalUsd")),
                        plannedEntryAt: toInstant(data.get("plannedEntryAt")),
                        plannedExitAt: toInstant(data.get("plannedExitAt")),
                        entryAttemptCount: toNum(data.get("entryAttemptCount")),
                        entrySpacingMs: toNum(data.get("entrySpacingMs")),
                        manualLatencyAdjustmentMs: toNum(data.get("manualLatencyAdjustmentMs")),
                        stopLossUsd: toNum(data.get("stopLossUsd")),
                        takeProfitUsd: toNum(data.get("takeProfitUsd")),
                        notes: data.get("notes") || null
                    });
                    if (onRefresh) onRefresh();
                    await openTradeDetail({ id, nodes, showError, onRefresh });
                } catch (err) {
                    showError(err.message);
                    submitBtn.disabled = false;
                    submitBtn.textContent = "Сохранить изменения";
                }
            });
        }
    } catch (error) {
        showError(error.message);
    }
}
