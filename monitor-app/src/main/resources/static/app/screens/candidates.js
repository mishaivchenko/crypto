import { api } from "../../api.js";
import { emptyState, candidateCard } from "../shared.js";
import { t } from "../../i18n.js";

export function candidatesListMarkup(page = {}, { aiEnabled = false, liquidityMap = {} } = {}) {
    const candidates = page?.content ?? [];
    return candidates.length
        ? candidates.map((c) => candidateCard(c, { aiEnabled, liquidity: liquidityMap[c.id] ?? null })).join("")
        : emptyState(t("empty_candidates"), t("empty_candidates_detail"));
}

export async function renderCandidates({ nodes, page, showError, onRefresh }) {
    const candidates = page?.content ?? [];

    let aiEnabled = false;
    const liquidityMap = {};

    const [aiStatus] = await Promise.allSettled([
        api.getAiStatus().catch(() => ({ enabled: false }))
    ]);
    if (aiStatus.status === "fulfilled") aiEnabled = Boolean(aiStatus.value?.enabled);

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

    nodes.candidatesList.innerHTML = candidatesListMarkup(page, { aiEnabled, liquidityMap });

    if (onRefresh && !nodes.candidatesList._actionsWired) {
        nodes.candidatesList._actionsWired = true;
        wireSignalCardActions(nodes.candidatesList, { showError, onRefresh });
    }
}

function wireSignalCardActions(container, { showError, onRefresh }) {
    container.addEventListener("click", async (event) => {
        const toggleBtn = event.target.closest("[data-action='toggle-signal-card']");
        if (toggleBtn) {
            const card = toggleBtn.closest(".signal-card");
            if (!card) return;
            const expanded = card.classList.toggle("is-expanded");
            toggleBtn.setAttribute("aria-expanded", String(expanded));
            toggleBtn.textContent = expanded ? "▴" : "▾";
            return;
        }

        const approveBtn = event.target.closest("[data-action='quick-approve-candidate']");
        if (approveBtn) {
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
                await onRefresh();
            } catch (error) {
                analyzeBtn.disabled = false;
                analyzeBtn.textContent = origText;
                showError(error.message);
            }
            return;
        }

        const assessBtn = event.target.closest("[data-action='assess-card-liquidity']");
        if (assessBtn) {
            const origText = assessBtn.textContent;
            assessBtn.disabled = true;
            assessBtn.textContent = t("liquidity_refreshing");
            try {
                const { id, venue, symbol } = assessBtn.dataset;
                await api.assessCandidateLiquidity(id, venue, symbol);
                await onRefresh();
            } catch (error) {
                assessBtn.disabled = false;
                assessBtn.textContent = origText;
                showError(error.message);
            }
        }
    });
}
