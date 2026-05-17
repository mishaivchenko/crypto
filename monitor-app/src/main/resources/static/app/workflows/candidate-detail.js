import { api } from "../../api.js";
import {
    escapeHtml,
    formatBadge,
    formatDecimal,
    formatInstant,
    formatRelative,
    kv,
    metaRow,
    openModal,
    pipelineStageMarkup,
    section,
    sourceLabel,
    toLocalInputValue
} from "../shared.js";
import { buildDeleteCandidateSection } from "./pipeline.js";
import { t } from "../../i18n.js";

function recommendationRows(candidate) {
    return [
        kv(t("event_source"), candidate.sourceVenue ?? sourceLabel(candidate.sourceType)),
        kv(t("candidate_canonical_symbol"), candidate.normalizedSymbol ?? "—"),
        kv("Suggested venue", candidate.suggestedVenue ?? candidate.sourceVenue ?? "Manual select"),
        kv("Suggested funding time", formatInstant(candidate.suggestedFundingTime)),
        kv(t("candidate_funding_rate"), formatDecimal(candidate.suggestedFundingRatePct, 6))
    ].join("");
}

function buildApproveSection(candidate) {
    if (candidate.status === "EVENT_CREATED") {
        return section(
            t("candidate_event_created_title"),
            `
                <div class="action-card primary">
                    <p class="helper-text">${t("candidate_event_created_detail")}${escapeHtml(candidate.fundingEventId)}.</p>
                </div>
            `
        );
    }
    if (candidate.status === "REJECTED") {
        return section(
            t("candidate_closed_title"),
            `
                <div class="action-card">
                    <p class="helper-text">${t("candidate_closed_detail")}</p>
                </div>
            `
        );
    }

    const venue = candidate.suggestedVenue ?? candidate.sourceVenue ?? candidate.venueHints?.[0] ?? "";
    const symbol = candidate.normalizedSymbol ?? candidate.rawSymbol ?? "";
    const fundingTime = toLocalInputValue(candidate.suggestedFundingTime);
    const fundingRatePct = candidate.suggestedFundingRatePct ?? "";
    const actionLabel = candidate.status === "FAILED" ? t("candidate_repair_label") : t("candidate_approve_label");
    const helper = candidate.status === "FAILED"
        ? t("candidate_repair_helper")
        : t("candidate_approve_helper");

    return section(
        candidate.status === "FAILED" ? t("candidate_repair_title") : t("candidate_approve_title"),
        `
            <div class="action-card primary">
                <p class="helper-text">${escapeHtml(helper)}</p>
                <div class="detail-grid action-note">
                    ${recommendationRows(candidate)}
                </div>
                <form class="drawer-form" data-action="approve-candidate" data-id="${candidate.id}">
                    <div class="drawer-form-row labeled-row">
                        <label class="field">
                            <span>${t("candidate_venue_label")}</span>
                            <input name="venue" type="text" placeholder="${t("candidate_venue_placeholder")}" value="${escapeHtml(venue)}">
                        </label>
                        <label class="field">
                            <span>${t("candidate_symbol_label")}</span>
                            <input name="symbol" type="text" placeholder="${t("candidate_symbol_placeholder")}" value="${escapeHtml(symbol)}">
                        </label>
                    </div>
                    <div class="drawer-form-row labeled-row">
                        <label class="field">
                            <span>${t("candidate_funding_time")}</span>
                            <input name="fundingTime" type="datetime-local" value="${escapeHtml(fundingTime)}">
                        </label>
                        <label class="field">
                            <span>${t("candidate_funding_rate")}</span>
                            <input name="fundingRatePct" type="number" step="0.000001" placeholder="-0.012500" value="${escapeHtml(fundingRatePct)}">
                        </label>
                    </div>
                    <label class="field">
                        <span>${t("candidate_operator_note")}</span>
                        <textarea name="reviewNotes" placeholder="${t("candidate_operator_note_placeholder")}">${escapeHtml(candidate.reviewNotes ?? "")}</textarea>
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
        t("candidate_reject_title"),
        `
            <div class="action-card">
                <p class="helper-text">${t("candidate_reject_helper")}</p>
                <form class="drawer-form" data-action="reject-candidate" data-id="${candidate.id}">
                    <label class="field">
                        <span>${t("candidate_reject_note")}</span>
                        <textarea name="reviewNotes" placeholder="${t("candidate_reject_note_placeholder")}"></textarea>
                    </label>
                    <div class="actions">
                        <button class="button danger" type="submit">${t("candidate_reject_button")}</button>
                    </div>
                </form>
            </div>
        `
    );
}

export function buildCandidateDrawerContent(candidate) {
    return `
        ${pipelineStageMarkup("signal")}
        ${section(t("candidate_signal_snapshot"), `
            <div class="meta-grid">
                ${metaRow(t("candidate_status"), formatBadge("candidate", candidate.status))}
                ${metaRow(t("candidate_detected_at"), formatInstant(candidate.detectedAt), formatRelative(candidate.detectedAt))}
                ${metaRow(t("candidate_source_venue"), escapeHtml(candidate.sourceVenue ?? "—"))}
                ${metaRow(t("candidate_raw_symbol"), escapeHtml(candidate.rawSymbol))}
                ${metaRow(t("candidate_canonical_symbol"), escapeHtml(candidate.normalizedSymbol ?? "—"))}
                ${metaRow(t("candidate_venue_hints"), escapeHtml(candidate.venueHints?.join(", ") || "—"))}
                ${metaRow(t("candidate_review"), escapeHtml(candidate.reviewDecision ?? t("candidate_review_pending")))}
                ${candidate.fundingEventId
                    ? metaRow(t("candidate_funding_event"), `<button class="button secondary small" type="button" data-open-event="${candidate.fundingEventId}">→ Event #${candidate.fundingEventId}</button>`)
                    : metaRow(t("candidate_funding_event"), "—")}
            </div>
        `)}
        ${candidate.normalizationFailureReason ? section(t("candidate_norm_note"), `
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
        nodes.modalType.textContent = t("candidate_modal_type");
        nodes.modalTitle.textContent = candidate.normalizedSymbol ?? candidate.rawSymbol;
        nodes.modalContent.innerHTML = buildCandidateDrawerContent(candidate);
        openModal(nodes);
    } catch (error) {
        showError(error.message);
    }
}
