import { api } from "../../api.js";
import { tradeHistoryDetailMarkup } from "../../history.js";
import { optionalRequest, pipelineStageMarkup } from "../shared.js";

export function buildHistoryTradeDrawerContent(payload) {
    return pipelineStageMarkup("trade") + tradeHistoryDetailMarkup(payload);
}

export async function openHistoryTradeDetail({ id, nodes, showError }) {
    try {
        const trade = await api.getArmedTrade(id);
        const [event, candidate, journal] = await Promise.all([
            api.getFundingEvent(trade.fundingEventId),
            trade.signalCandidateId ? optionalRequest(() => api.getCandidate(trade.signalCandidateId)) : Promise.resolve(null),
            api.listArmedTradeJournal(id)
        ]);
        const attempts = await api.listOrderAttempts(id);

        nodes.drawerType.textContent = "Trade History";
        nodes.drawerTitle.textContent = trade.symbol ? `${trade.symbol} · ${trade.venue}` : `Trade #${trade.id}`;
        nodes.drawerContent.innerHTML = buildHistoryTradeDrawerContent({ trade, event, candidate, journal, attempts });
    } catch (error) {
        showError(error.message);
    }
}
