import { api } from "../../api.js";
import {
    escapeHtml,
    formatBadge,
    formatDecimal,
    formatFundingCountdown,
    formatInstant,
    journalMarkup,
    metaRow,
    offsetIso,
    openModal,
    pipelineStageMarkup,
    section,
    sourceLabel,
    toLocalInputValue
} from "../shared.js";
import { buildDeleteCandidateSection } from "./pipeline.js";
import { t } from "../../i18n.js";

export function buildEventDrawerContent({ event, journal }) {
    const defaultEntry = toLocalInputValue(offsetIso(event.fundingTime, -45));
    const defaultExit = toLocalInputValue(offsetIso(event.fundingTime, 90));
    const canArm = event.status === "DISCOVERED";

    return `
        ${pipelineStageMarkup("event")}
        ${section(t("event_snapshot"), `
            <div class="meta-grid">
                ${metaRow(t("event_status"), formatBadge("event", event.status))}
                ${metaRow(t("event_funding_time"), formatInstant(event.fundingTime), formatFundingCountdown(event.fundingTime))}
                ${metaRow(t("event_funding_rate"), formatDecimal(event.fundingRatePct, 6))}
                ${metaRow(t("event_venue"), escapeHtml(event.venue))}
                ${metaRow(t("event_source"), escapeHtml(sourceLabel(event.sourceType)))}
                ${metaRow(t("event_linked_signal"), event.signalCandidateId ? `#${event.signalCandidateId}` : t("label_manual"))}
                ${event.armedTradeId
                    ? metaRow(t("event_prepared_trade"), `<button class="button secondary small" type="button" data-open-trade="${event.armedTradeId}">→ Trade #${event.armedTradeId}</button>`)
                    : metaRow(t("event_prepared_trade"), "—")}
            </div>
        `)}
        ${section(t("event_arm_title"), canArm ? `
            <div class="action-card primary">
                <p class="helper-text">${t("event_arm_helper")}</p>
                <form class="drawer-form" data-action="arm-event" data-id="${event.id}">
                    <fieldset class="form-group">
                        <legend>${t("event_entry_window")}</legend>
                        <div class="drawer-form-row labeled-row">
                            <label class="field">
                                <span>${t("event_notional_usd")}</span>
                                <input name="notionalUsd" type="number" step="0.01" placeholder="25" value="25">
                            </label>
                            <label class="field">
                                <span>${t("event_side")}</span>
                                <input name="intendedSide" type="text" value="SHORT" readonly>
                                <small>${t("event_short_only")}</small>
                            </label>
                        </div>
                        <div class="drawer-form-row labeled-row">
                            <label class="field">
                                <span>${t("event_planned_entry")}</span>
                                <input name="plannedEntryAt" type="datetime-local" value="${escapeHtml(defaultEntry)}">
                            </label>
                            <label class="field">
                                <span>${t("event_entry_attempts")}</span>
                                <input name="entryAttemptCount" type="number" min="1" max="25" step="1" value="3">
                            </label>
                        </div>
                        <div class="drawer-form-row labeled-row">
                            <label class="field">
                                <span>${t("event_spacing_ms")}</span>
                                <input name="entrySpacingMs" type="number" min="0" step="1" value="150">
                            </label>
                            <label class="field">
                                <span>${t("event_manual_latency")}</span>
                                <input name="manualLatencyAdjustmentMs" type="number" min="-60000" max="60000" step="1" value="0">
                                <small>${t("event_engine_triggers_earlier")}</small>
                            </label>
                        </div>
                    </fieldset>
                    <fieldset class="form-group">
                        <legend>${t("event_exit_window")}</legend>
                        <label class="field">
                            <span>${t("event_planned_exit")}</span>
                            <input name="plannedExitAt" type="datetime-local" value="${escapeHtml(defaultExit)}">
                        </label>
                    </fieldset>
                    <fieldset class="form-group">
                        <legend>${t("event_risk_management")}</legend>
                        <div class="drawer-form-row labeled-row">
                            <label class="field">
                                <span>${t("event_stop_loss")}</span>
                                <input name="stopLossUsd" type="number" step="0.01" min="0" placeholder="${t("event_stop_loss_placeholder")}">
                                <small>${t("event_stop_loss_note")}</small>
                            </label>
                            <label class="field">
                                <span>${t("event_take_profit")}</span>
                                <input name="takeProfitUsd" type="number" step="0.01" min="0" placeholder="${t("event_take_profit_placeholder")}">
                                <small>${t("event_take_profit_note")}</small>
                            </label>
                        </div>
                    </fieldset>
                    <label class="field">
                        <span>${t("event_prep_note")}</span>
                        <textarea name="notes" placeholder="${t("event_prep_note_placeholder")}"></textarea>
                    </label>
                    <div class="actions">
                        <button class="button" type="submit">${t("event_create_trade")}</button>
                    </div>
                </form>
            </div>
        ` : `
            <div class="action-card">
                <p class="helper-text">${t("event_already_armed")} ${escapeHtml(event.status.toLowerCase())} ${t("event_cannot_arm")}</p>
            </div>
        `)}
        ${event.signalCandidateId ? buildDeleteCandidateSection({ id: event.signalCandidateId }, t("event_delete_source")) : ""}
        ${section("Journal", journalMarkup(journal))}
    `;
}

export async function openEventDetail({ id, nodes, showError }) {
    try {
        const [event, journal] = await Promise.all([
            api.getFundingEvent(id),
            api.listFundingEventJournal(id)
        ]);

        nodes.modalType.textContent = t("event_modal_type");
        nodes.modalTitle.textContent = `${event.symbol} · ${event.venue}`;
        nodes.modalContent.innerHTML = buildEventDrawerContent({ event, journal });
        openModal(nodes);
    } catch (error) {
        showError(error.message);
    }
}
