import { api } from "../../api.js";
import { tradeHistoryDetailMarkup } from "../../history.js";
import { escapeHtml, openModal, optionalRequest, pipelineStageMarkup, section } from "../shared.js";

const CANCELLABLE_STATES = new Set(["ARMED", "ENTRY_PENDING", "ENTRY_ATTEMPTED", "OPEN", "EXIT_PENDING"]);

export function buildHistoryTradeDrawerContent(payload) {
    return pipelineStageMarkup("trade") + tradeHistoryDetailMarkup(payload);
}


export async function openHistoryTradeDetail({ id, nodes, showError, onRefresh }) {
    try {
        const trade = await api.getArmedTrade(id);
        const [event, candidate, journal, position, outcome] = await Promise.all([
            api.getFundingEvent(trade.fundingEventId),
            trade.signalCandidateId ? optionalRequest(() => api.getCandidate(trade.signalCandidateId)) : Promise.resolve(null),
            api.listArmedTradeJournal(id),
            api.getTradePosition(id),
            api.getTradeOutcome(id)
        ]);
        const attempts = await api.listOrderAttempts(id);

        nodes.modalType.textContent = "Trade History";
        nodes.modalTitle.textContent = trade.symbol ? `${trade.symbol} · ${trade.venue}` : `Trade #${trade.id}`;

        let cancelHtml = "";
        if (CANCELLABLE_STATES.has(trade.state)) {
            cancelHtml = section("Cancel trade", `
                <p class="muted">Переведёт сделку в CANCELLED. Открытые позиции на бирже не закрываются — только запись в системе.</p>
                <button class="button danger" type="button" data-cancel-trade="${trade.id}">Отменить сделку</button>
            `);
        }

        nodes.modalContent.innerHTML = buildHistoryTradeDrawerContent({ trade, event, candidate, journal, attempts, position, outcome }) + cancelHtml;
        openModal(nodes);

        const cancelBtn = nodes.modalContent.querySelector("[data-cancel-trade]");
        if (cancelBtn) {
            cancelBtn.addEventListener("click", async () => {
                cancelBtn.disabled = true;
                cancelBtn.textContent = "Отменяем…";
                try {
                    await api.cancelArmedTrade(trade.id);
                    if (onRefresh) onRefresh();
                    await openHistoryTradeDetail({ id, nodes, showError, onRefresh });
                } catch (err) {
                    showError(err.message);
                    cancelBtn.disabled = false;
                    cancelBtn.textContent = "Отменить сделку";
                }
            });
        }
    } catch (error) {
        showError(error.message);
    }
}
