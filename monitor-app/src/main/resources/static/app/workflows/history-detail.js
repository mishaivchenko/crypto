import { api } from "../../api.js";
import { tradeHistoryDetailMarkup } from "../../history.js";
import { escapeHtml, optionalRequest, pipelineStageMarkup, section } from "../shared.js";

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

        nodes.drawerType.textContent = "Trade History";
        nodes.drawerTitle.textContent = trade.symbol ? `${trade.symbol} · ${trade.venue}` : `Trade #${trade.id}`;

        let cancelHtml = "";
        if (CANCELLABLE_STATES.has(trade.state)) {
            cancelHtml = section("Cancel trade", `
                <p class="muted">Переведёт сделку в CANCELLED. Открытые позиции на бирже не закрываются — только запись в системе.</p>
                <button class="button danger" type="button" data-cancel-trade="${trade.id}">Отменить сделку</button>
            `);
        }

        nodes.drawerContent.innerHTML = buildHistoryTradeDrawerContent({ trade, event, candidate, journal, attempts, position, outcome }) + cancelHtml;

        const cancelBtn = nodes.drawerContent.querySelector("[data-cancel-trade]");
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
