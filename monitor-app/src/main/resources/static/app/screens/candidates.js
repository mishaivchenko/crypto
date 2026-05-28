import { api } from "../../api.js";
import { emptyState, candidateCard } from "../shared.js";
import { buildApproveSection } from "../workflows/candidate-detail.js";
import { buildDeleteCandidateSection } from "../workflows/pipeline.js";
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
        wireRepairExpansion(nodes.candidatesList, { showError, onRefresh });
    }
}

function wireRepairExpansion(container, { showError, onRefresh }) {
    container.addEventListener("toggle", async (e) => {
        const details = e.target.closest("[data-lazy-candidate-repair]");
        if (!details || !details.open) return;

        const candidateId = details.dataset.lazyCandidateRepair;
        const contentEl = details.querySelector(".card-full-content");
        if (!contentEl || !contentEl.querySelector(".card-loading")) return;

        try {
            const candidate = await api.getCandidate(candidateId);
            contentEl.innerHTML = buildApproveSection(candidate) + buildDeleteCandidateSection(candidate, t("candidate_delete_label"));

            wireApproveForm(contentEl, { showError, onRefresh });
            wireDeleteCandidate(contentEl, { showError, onRefresh });
        } catch (err) {
            contentEl.innerHTML = `<p class="card-loading">${err.message}</p>`;
        }
    }, true);
}

function wireApproveForm(container, { showError, onRefresh }) {
    const form = container.querySelector("[data-action='approve-candidate']");
    if (!form) return;
    form.addEventListener("submit", async (e) => {
        e.preventDefault();
        const data = new FormData(form);
        const id = form.dataset.id;
        const btn = form.querySelector("[type='submit']");
        btn.disabled = true;
        const orig = btn.textContent;
        btn.textContent = "…";
        try {
            await api.approveCandidate(id, {
                venue: data.get("venue") || null,
                symbol: data.get("symbol") || null,
                fundingTime: data.get("fundingTime") ? new Date(data.get("fundingTime")).toISOString() : null,
                fundingRatePct: data.get("fundingRatePct") ? Number(data.get("fundingRatePct")) : null,
                reviewNotes: data.get("reviewNotes") || null
            });
            if (onRefresh) await onRefresh();
        } catch (err) {
            btn.disabled = false;
            btn.textContent = orig;
            showError(err.message);
        }
    });
}

function wireDeleteCandidate(container, { showError, onRefresh }) {
    const form = container.querySelector("[data-action='delete-candidate']");
    if (!form) return;
    form.addEventListener("submit", async (e) => {
        e.preventDefault();
        if (!window.confirm(t("candidate_delete_confirm"))) return;
        const data = new FormData(form);
        const id = form.dataset.id;
        const btn = form.querySelector("[type='submit']");
        btn.disabled = true;
        try {
            await api.deleteCandidate(id, data.get("deleteNote") ?? "");
            if (onRefresh) await onRefresh();
        } catch (err) {
            btn.disabled = false;
            showError(err.message);
        }
    });
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
    const candidate = await api.getCandidate(candidateId);
    const liquidity = await api.getCandidateLiquidity(candidateId);
    const article = container.querySelector(`[data-candidate-id="${candidateId}"]`);
    if (!article) return;
    article.outerHTML = candidateCard(candidate, { liquidity: liquidity ?? null });
}
