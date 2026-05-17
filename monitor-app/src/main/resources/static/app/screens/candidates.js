import { emptyState, candidateCard, wireOpenButtons } from "../shared.js";
import { t } from "../../i18n.js";

export function candidatesListMarkup(page = {}) {
    const candidates = page?.content ?? [];
    return candidates.length
        ? candidates.map(candidateCard).join("")
        : emptyState(t("empty_candidates"), t("empty_candidates_detail"));
}

export function renderCandidates({ nodes, page, onOpenCandidate }) {
    nodes.candidatesList.innerHTML = candidatesListMarkup(page);
    wireOpenButtons(nodes.candidatesList, "[data-open-candidate]", onOpenCandidate);
}
