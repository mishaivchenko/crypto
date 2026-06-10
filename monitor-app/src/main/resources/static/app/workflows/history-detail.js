import { api } from "../../api.js";
import { deriveHistoryStage, deriveTradeHealth, tradeHistoryDetailMarkup } from "../../history.js";
import { escapeHtml, formatBadge, formatDecimal, openModal, optionalRequest, pipelineStageMarkup, section, venueIcon } from "../shared.js";
import { t } from "../../i18n.js";
import { renderEnrichmentTimeline } from "../components/enrichment-timeline.js";

const CANCELLABLE_STATES = new Set(["ARMED", "ENTRY_PENDING", "ENTRY_ATTEMPTED", "OPEN", "EXIT_PENDING"]);

export function buildHistoryTradeDrawerContent({ trade, event, candidate, journal, attempts = [], position = null, outcome = null }) {
    const historyStage = deriveHistoryStage(trade, event, attempts);
    const health = deriveTradeHealth(trade, attempts, event);
    const net = outcome?.netPnlUsd != null ? Number(outcome.netPnlUsd) : null;
    const pnlChip = net != null
        ? `<span class="chip chip-${net >= 0 ? "good" : "bad"}">${net >= 0 ? "+" : ""}${formatDecimal(net, 4)} USD</span>`
        : "";

    const summaryChips = `
        <div class="chip-row" style="margin-bottom:4px">
            ${formatBadge("historyStage", historyStage.code)}
            <span class="chip chip-${escapeHtml(health.tone)}">${escapeHtml(health.label)}</span>
            ${pnlChip}
        </div>
    `;

    const enrichmentLayers = [];

    // 1. Liquidity snapshot at armedAt
    if (trade.liquidityAssessment) {
        const liq = trade.liquidityAssessment;
        const liqStatus = liq.score === "EXCELLENT" || liq.score === "GOOD" ? "ok"
            : liq.score === "THIN" ? "warn"
            : liq.score === "UNTRADABLE" ? "blocked" : "missing";
        enrichmentLayers.push({
            name: "Liquidity Snapshot",
            status: liqStatus,
            timestamp: liq.sampledAt ?? null,
            decorator: "ORDER_BOOK",
            details: `Score: ${liq.score ?? "—"} · Spread: ${liq.spreadBps != null ? liq.spreadBps + " bps" : "—"}`
        });
    }

    // 2. Latency chain
    if (trade.warmupDoneAt || trade.effectiveEntryLatencyMs != null) {
        const latMs = trade.effectiveEntryLatencyMs ?? trade.measuredEntryLatencyMs;
        const latStatus = !latMs ? "missing" : latMs < 400 ? "ok" : latMs < 600 ? "warn" : "blocked";
        enrichmentLayers.push({
            name: "Latency Chain",
            status: latStatus,
            timestamp: trade.warmupDoneAt ?? null,
            decorator: "WARMUP_PROBE",
            details: `p50: ${trade.warmupP50Ms ?? "—"}мс · Effective: ${latMs ?? "—"}мс`
        });
    }

    // 3. AI Advice
    if (candidate?.aiAdvice) {
        const ai = candidate.aiAdvice;
        const aiStatus = ai.recommendation === "GO" ? "ok"
            : ai.recommendation === "WATCH" ? "warn"
            : ai.recommendation === "PASS" ? "blocked" : "missing";
        enrichmentLayers.push({
            name: "AI Advice",
            status: aiStatus,
            timestamp: ai.analyzedAt ?? null,
            decorator: "AI_ADVISOR",
            details: `${ai.recommendation} · ${Math.round(ai.confidence * 100)}%`
        });
    }

    // 4. Execution (first attempt)
    const firstAttempt = attempts?.[0] ?? null;
    if (firstAttempt) {
        const execStatus = firstAttempt.status === "FILLED" ? "ok"
            : firstAttempt.status === "FAILED" ? "blocked" : "warn";
        enrichmentLayers.push({
            name: "Исполнение",
            status: execStatus,
            timestamp: firstAttempt.triggerAt ?? null,
            decorator: "ENGINE",
            details: `${firstAttempt.status} · ${firstAttempt.requestDurationMs != null ? firstAttempt.requestDurationMs + "мс" : "—"}`
        });
    }

    const timelineHtml = enrichmentLayers.length > 0
        ? `<details class="enrichment-log-section" data-enrichment-section="true" open>
             <summary style="cursor:pointer;font-size:13px;font-weight:600;padding:4px 0">
               На момент запуска — состояние обогащения
             </summary>
             ${renderEnrichmentTimeline(enrichmentLayers)}
           </details>`
        : "";

    return pipelineStageMarkup("executed") + summaryChips + tradeHistoryDetailMarkup({ trade, event, candidate, journal, attempts, position, outcome }) + timelineHtml;
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

        nodes.modalType.textContent = t("history_modal_type");
        nodes.modalTitle.innerHTML = trade.symbol
            ? `${venueIcon(trade.venue)}${escapeHtml(trade.symbol)} · ${escapeHtml(trade.venue)}`
            : escapeHtml(`${t("history_trade_prefix")}${trade.id}`);

        let cancelHtml = "";
        if (CANCELLABLE_STATES.has(trade.state)) {
            cancelHtml = section(t("trade_cancel_title"), `
                <p class="muted">${t("trade_cancel_detail")}</p>
                <button class="button danger" type="button" data-cancel-trade="${trade.id}">${t("trade_cancel_button")}</button>
            `);
        }

        nodes.modalContent.innerHTML = buildHistoryTradeDrawerContent({ trade, event, candidate, journal, attempts, position, outcome }) + cancelHtml;
        openModal(nodes);

        const cancelBtn = nodes.modalContent.querySelector("[data-cancel-trade]");
        if (cancelBtn) {
            cancelBtn.addEventListener("click", async () => {
                cancelBtn.disabled = true;
                cancelBtn.textContent = t("trade_cancelling");
                try {
                    await api.cancelArmedTrade(trade.id);
                    if (onRefresh) onRefresh();
                    await openHistoryTradeDetail({ id, nodes, showError, onRefresh });
                } catch (err) {
                    showError(err.message);
                    cancelBtn.disabled = false;
                    cancelBtn.textContent = t("trade_cancel_button");
                }
            });
        }
    } catch (error) {
        showError(error.message);
    }
}
