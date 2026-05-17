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
    pipelineStageMarkup,
    section,
    sourceLabel,
    toLocalInputValue
} from "../shared.js";
import { buildDeleteCandidateSection } from "./pipeline.js";

export function buildEventDrawerContent({ event, journal }) {
    const defaultEntry = toLocalInputValue(offsetIso(event.fundingTime, -45));
    const defaultExit = toLocalInputValue(offsetIso(event.fundingTime, 90));
    const canArm = event.status === "DISCOVERED";

    return `
        ${pipelineStageMarkup("event")}
        ${section("Event snapshot", `
            <div class="meta-grid">
                ${metaRow("Статус", formatBadge("event", event.status))}
                ${metaRow("Funding time", formatInstant(event.fundingTime), formatFundingCountdown(event.fundingTime))}
                ${metaRow("Funding rate", formatDecimal(event.fundingRatePct, 6))}
                ${metaRow("Venue", escapeHtml(event.venue))}
                ${metaRow("Source", escapeHtml(sourceLabel(event.sourceType)))}
                ${metaRow("Linked signal", event.signalCandidateId ? `#${event.signalCandidateId}` : "manual")}
                ${event.armedTradeId
                    ? metaRow("Prepared Trade", `<button class="button secondary small" type="button" data-open-trade="${event.armedTradeId}">→ Trade #${event.armedTradeId}</button>`)
                    : metaRow("Prepared Trade", "—")}
            </div>
        `)}
        ${section("Arm Prepared Trade", canArm ? `
            <div class="action-card primary">
                <p class="helper-text">Создай Prepared Trade для engine flow. Реальный order здесь не ставится.</p>
                <form class="drawer-form" data-action="arm-event" data-id="${event.id}">
                    <div class="drawer-form-row labeled-row">
                        <label class="field">
                            <span>Notional, USD</span>
                            <input name="notionalUsd" type="number" step="0.01" placeholder="25" value="25">
                        </label>
                        <label class="field">
                            <span>Side</span>
                            <input name="intendedSide" type="text" value="SHORT" readonly>
                            <small>Funding strategy is SHORT-only.</small>
                        </label>
                    </div>
                    <div class="drawer-form-row labeled-row">
                        <label class="field">
                            <span>Planned entry</span>
                            <input name="plannedEntryAt" type="datetime-local" value="${escapeHtml(defaultEntry)}">
                        </label>
                        <label class="field">
                            <span>Planned exit</span>
                            <input name="plannedExitAt" type="datetime-local" value="${escapeHtml(defaultExit)}">
                        </label>
                    </div>
                    <div class="drawer-form-row labeled-row">
                        <label class="field">
                            <span>Entry attempts</span>
                            <input name="entryAttemptCount" type="number" min="1" max="25" step="1" value="3">
                        </label>
                        <label class="field">
                            <span>Spacing, ms</span>
                            <input name="entrySpacingMs" type="number" min="0" step="1" value="150">
                        </label>
                    </div>
                    <label class="field">
                        <span>Manual latency adj, ms</span>
                        <input name="manualLatencyAdjustmentMs" type="number" min="-60000" max="60000" step="1" value="0">
                        <small>Engine will trigger attempts earlier by measured latency plus this adjustment.</small>
                    </label>
                    <div class="drawer-form-row labeled-row">
                        <label class="field">
                            <span>Stop Loss, USD</span>
                            <input name="stopLossUsd" type="number" step="0.01" min="0" placeholder="e.g. 50.00">
                            <small>Max loss before auto-exit. Leave blank to disable.</small>
                        </label>
                        <label class="field">
                            <span>Take Profit, USD</span>
                            <input name="takeProfitUsd" type="number" step="0.01" min="0" placeholder="e.g. 50.00">
                            <small>Target gain before auto-exit. Leave blank to disable.</small>
                        </label>
                    </div>
                    <label class="field">
                        <span>Preparation note</span>
                        <textarea name="notes" placeholder="Почему этот Event должен перейти в Prepared Trade"></textarea>
                    </label>
                    <div class="actions">
                        <button class="button" type="submit">Create Prepared Trade</button>
                    </div>
                </form>
            </div>
        ` : `
            <div class="action-card">
                <p class="helper-text">Event уже находится в статусе ${escapeHtml(event.status.toLowerCase())} и больше не может быть armed из этого desk.</p>
            </div>
        `)}
        ${event.signalCandidateId ? buildDeleteCandidateSection({ id: event.signalCandidateId }, "Delete source signal") : ""}
        ${section("Journal", journalMarkup(journal))}
    `;
}

export async function openEventDetail({ id, nodes, showError }) {
    try {
        const [event, journal] = await Promise.all([
            api.getFundingEvent(id),
            api.listFundingEventJournal(id)
        ]);

        nodes.drawerType.textContent = "Funding Event";
        nodes.drawerTitle.textContent = `${event.symbol} · ${event.venue}`;
        nodes.drawerContent.innerHTML = buildEventDrawerContent({ event, journal });
    } catch (error) {
        showError(error.message);
    }
}
