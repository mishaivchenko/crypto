import { emptyState, candidateCard, wireOpenButtons } from "../shared.js";

export function candidatesListMarkup(page = {}) {
    const candidates = page?.content ?? [];
    return candidates.length
        ? candidates.map(candidateCard).join("")
        : emptyState("Signal Queue пуста.", "Новые candidates из Funding API появятся здесь.");
}

export function renderCandidates({ nodes, page, onOpenCandidate }) {
    nodes.candidatesList.innerHTML = candidatesListMarkup(page);
    wireOpenButtons(nodes.candidatesList, "[data-open-candidate]", onOpenCandidate);
}
