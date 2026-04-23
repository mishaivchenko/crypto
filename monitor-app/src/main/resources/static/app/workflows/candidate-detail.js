import { api } from "../../api.js";
import {
    escapeHtml,
    formatBadge,
    formatDecimal,
    formatInstant,
    formatRelative,
    kv,
    metaRow,
    section,
    sourceLabel,
    toLocalInputValue
} from "../shared.js";
import { buildDeleteCandidateSection } from "./pipeline.js";

function recommendationRows(candidate) {
    return [
        kv("Source", candidate.sourceVenue ?? sourceLabel(candidate.sourceType)),
        kv("Canonical symbol", candidate.normalizedSymbol ?? "Не определён"),
        kv("Suggested venue", candidate.suggestedVenue ?? candidate.sourceVenue ?? "Manual select"),
        kv("Suggested funding time", formatInstant(candidate.suggestedFundingTime)),
        kv("Funding rate", formatDecimal(candidate.suggestedFundingRatePct, 6))
    ].join("");
}

function buildApproveSection(candidate) {
    if (candidate.status === "EVENT_CREATED") {
        return section(
            "Funding Event уже создан",
            `
                <div class="action-card primary">
                    <p class="helper-text">Этот signal уже переведён в Funding Event #${escapeHtml(candidate.fundingEventId)}.</p>
                </div>
            `
        );
    }
    if (candidate.status === "REJECTED") {
        return section(
            "Candidate закрыт",
            `
                <div class="action-card">
                    <p class="helper-text">Signal уже отклонён и больше не участвует в operator flow.</p>
                </div>
            `
        );
    }

    const venue = candidate.suggestedVenue ?? candidate.sourceVenue ?? candidate.venueHints?.[0] ?? "";
    const symbol = candidate.normalizedSymbol ?? candidate.rawSymbol ?? "";
    const fundingTime = toLocalInputValue(candidate.suggestedFundingTime);
    const fundingRatePct = candidate.suggestedFundingRatePct ?? "";
    const actionLabel = candidate.status === "FAILED" ? "Исправить и create Event" : "Approve → Funding Event";
    const helper = candidate.status === "FAILED"
        ? "Signal нужно поправить вручную, прежде чем переводить его в Funding Event."
        : "Используй suggested venue и funding snapshot или переопредели поля перед approve.";

    return section(
        candidate.status === "FAILED" ? "Repair candidate" : "Approve to Funding Event",
        `
            <div class="action-card primary">
                <p class="helper-text">${escapeHtml(helper)}</p>
                <div class="detail-grid action-note">
                    ${recommendationRows(candidate)}
                </div>
                <form class="drawer-form" data-action="approve-candidate" data-id="${candidate.id}">
                    <div class="drawer-form-row labeled-row">
                        <label class="field">
                            <span>Venue</span>
                            <input name="venue" type="text" placeholder="Например, gate" value="${escapeHtml(venue)}">
                        </label>
                        <label class="field">
                            <span>Canonical symbol</span>
                            <input name="symbol" type="text" placeholder="Например, NOM/USDT" value="${escapeHtml(symbol)}">
                        </label>
                    </div>
                    <div class="drawer-form-row labeled-row">
                        <label class="field">
                            <span>Funding time</span>
                            <input name="fundingTime" type="datetime-local" value="${escapeHtml(fundingTime)}">
                        </label>
                        <label class="field">
                            <span>Funding rate, %</span>
                            <input name="fundingRatePct" type="number" step="0.000001" placeholder="-0.012500" value="${escapeHtml(fundingRatePct)}">
                        </label>
                    </div>
                    <label class="field">
                        <span>Operator note</span>
                        <textarea name="reviewNotes" placeholder="Почему этот signal стоит двигать дальше">${escapeHtml(candidate.reviewNotes ?? "")}</textarea>
                    </label>
                    <div class="actions">
                        <button class="button" type="submit">${escapeHtml(actionLabel)}</button>
                    </div>
                </form>
            </div>
        `
    );
}

function buildRejectSection(candidate) {
    if (candidate.status === "REJECTED" || candidate.status === "EVENT_CREATED") {
        return "";
    }
    return section(
        "Отклонить сигнал",
        `
            <div class="action-card">
                <p class="helper-text">Reject только если signal точно не должен становиться Funding Event.</p>
                <form class="drawer-form" data-action="reject-candidate" data-id="${candidate.id}">
                    <label class="field">
                        <span>Reject note</span>
                        <textarea name="reviewNotes" placeholder="Коротко зафиксируй причину отказа"></textarea>
                    </label>
                    <div class="actions">
                        <button class="button danger" type="submit">Reject candidate</button>
                    </div>
                </form>
            </div>
        `
    );
}

export function buildCandidateDrawerContent(candidate) {
    return `
        ${section("Signal snapshot", `
            <div class="meta-grid">
                ${metaRow("Статус", formatBadge("candidate", candidate.status))}
                ${metaRow("Detected at", formatInstant(candidate.detectedAt), formatRelative(candidate.detectedAt))}
                ${metaRow("Source venue", escapeHtml(candidate.sourceVenue ?? "—"))}
                ${metaRow("Raw symbol", escapeHtml(candidate.rawSymbol))}
                ${metaRow("Canonical symbol", escapeHtml(candidate.normalizedSymbol ?? "—"))}
                ${metaRow("Venue hints", escapeHtml(candidate.venueHints?.join(", ") || "—"))}
                ${metaRow("Review", escapeHtml(candidate.reviewDecision ?? "Pending"))}
                ${metaRow("Funding Event", candidate.fundingEventId ? `#${candidate.fundingEventId}` : "—")}
            </div>
        `)}
        ${candidate.normalizationFailureReason ? section("Normalization note", `
            <div class="action-card">
                <p class="helper-text">${escapeHtml(candidate.normalizationFailureReason)}</p>
            </div>
        `) : ""}
        ${buildApproveSection(candidate)}
        ${buildRejectSection(candidate)}
        ${candidate.status !== "DELETED" ? buildDeleteCandidateSection(candidate) : ""}
    `;
}

export async function openCandidateDetail({ id, nodes, showError }) {
    try {
        const candidate = await api.getCandidate(id);
        nodes.drawerType.textContent = "Signal";
        nodes.drawerTitle.textContent = candidate.normalizedSymbol ?? candidate.rawSymbol;
        nodes.drawerContent.innerHTML = buildCandidateDrawerContent(candidate);
    } catch (error) {
        showError(error.message);
    }
}
