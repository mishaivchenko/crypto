import { api } from "../../api.js";
import { emptyState, candidateCard } from "../shared.js";
import { t } from "../../i18n.js";

export function candidatesListMarkup(page = {}, { liquidityMap = {} } = {}) {
    const candidates = page?.content ?? [];
    if (!candidates.length) return emptyState(t("empty_candidates"), t("empty_candidates_detail"));
    const ready = candidates.filter(c => c.aiAdvice && liquidityMap[c.id]);
    if (!ready.length) return `<p class="muted candidates-analyzing">${t("candidates_all_analyzing")}</p>`;
    return ready.map((c) => candidateCard(c, { liquidity: liquidityMap[c.id] })).join("");
}

export async function renderCandidates({ nodes, page, showError, onRefresh }) {
    const candidates = page?.content ?? [];
    const liquidityMap = {};

    if (candidates.length) {
        await Promise.allSettled(
            candidates.map(async (c) => {
                try {
                    const liq = await api.getCandidateLiquidity(c.id);
                    if (liq) liquidityMap[c.id] = liq;
                } catch (_) { /* skip */ }
            })
        );
    }

    nodes.candidatesList.innerHTML = candidatesListMarkup(page, { liquidityMap });

    if (onRefresh && !nodes.candidatesList._actionsWired) {
        nodes.candidatesList._actionsWired = true;
        wireSignalCardActions(nodes.candidatesList, { showError, onRefresh });
    }
}

function wireSignalCardActions(container, { showError, onRefresh }) {
    container.addEventListener("click", async (event) => {
        const approveBtn = event.target.closest("[data-action='quick-approve-candidate']");
        if (approveBtn) {
            const symbol = approveBtn.dataset.symbol || approveBtn.dataset.venue || "this signal";
            if (!window.confirm(t("signal_approve_confirm", { symbol }))) return;
            approveBtn.disabled = true;
            const origText = approveBtn.textContent;
            approveBtn.textContent = "…";
            try {
                await api.approveCandidate(approveBtn.dataset.id, {
                    venue: approveBtn.dataset.venue || null,
                    symbol: approveBtn.dataset.symbol || null,
                    fundingTime: approveBtn.dataset.fundingTime || null,
                    fundingRatePct: approveBtn.dataset.fundingRate ? Number(approveBtn.dataset.fundingRate) : null,
                    reviewNotes: null
                });
                await onRefresh();
            } catch (error) {
                approveBtn.disabled = false;
                approveBtn.textContent = origText;
                showError(error.message);
            }
            return;
        }

        const rejectBtn = event.target.closest("[data-action='quick-reject-candidate']");
        if (rejectBtn) {
            rejectBtn.disabled = true;
            const origText = rejectBtn.textContent;
            rejectBtn.textContent = "…";
            try {
                await api.rejectCandidate(rejectBtn.dataset.id, { reviewNotes: null });
                await onRefresh();
            } catch (error) {
                rejectBtn.disabled = false;
                rejectBtn.textContent = origText;
                showError(error.message);
            }
            return;
        }

        const analyzeBtn = event.target.closest("[data-action='analyze-candidate'][data-id]");
        if (analyzeBtn && analyzeBtn.closest(".signal-card")) {
            const origText = analyzeBtn.textContent;
            analyzeBtn.disabled = true;
            analyzeBtn.textContent = t("ai_analyzing");
            try {
                await api.analyzeCandidate(analyzeBtn.dataset.id);
                await refreshCard(analyzeBtn.dataset.id, container);
            } catch (error) {
                analyzeBtn.disabled = false;
                analyzeBtn.textContent = origText;
                showError(error.message);
            }
            return;
        }

        const assessBtn = event.target.closest("[data-action='assess-card-liquidity']");
        if (assessBtn) {
            assessBtn.disabled = true;
            const origText = assessBtn.textContent;
            assessBtn.textContent = "…";
            try {
                await api.assessCandidateLiquidity(assessBtn.dataset.id);
                await refreshCard(assessBtn.dataset.id, container);
            } catch (error) {
                assessBtn.disabled = false;
                assessBtn.textContent = origText;
                showError(error.message);
            }
        }
    });
}

async function refreshCard(candidateId, container) {
    const [candidate, liquidity] = await Promise.all([
        api.getCandidate(candidateId),
        api.getCandidateLiquidity(candidateId)
    ]);
    const article = container.querySelector(`[data-candidate-id="${candidateId}"]`);
    if (!article) return;
    const newHtml = candidateCard(candidate, { liquidity: liquidity ?? null });
    article.outerHTML = newHtml;
}
