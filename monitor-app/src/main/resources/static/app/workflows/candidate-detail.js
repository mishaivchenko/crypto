import { api } from "../../api.js";
import {
    emptyState,
    escapeHtml,
    formatAiBadge,
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
import { renderEnrichmentTimeline } from "../components/enrichment-timeline.js";

function recommendationRows(candidate) {
    return [
        kv(t("event_source"), candidate.sourceVenue ?? sourceLabel(candidate.sourceType)),
        kv(t("candidate_canonical_symbol"), candidate.normalizedSymbol ?? "—"),
        kv("Suggested venue", candidate.suggestedVenue ?? candidate.sourceVenue ?? "Manual select"),
        kv("Suggested funding time", formatInstant(candidate.suggestedFundingTime)),
        kv(t("candidate_funding_rate"), formatDecimal(candidate.suggestedFundingRatePct, 6))
    ].join("");
}

export function buildApproveSection(candidate) {
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
                            <input name="fundingTime" type="datetime-local" step="0.001" value="${escapeHtml(fundingTime)}">
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

function buildCandidateLiquiditySection(liquidity, candidate) {
    const venue = candidate.suggestedVenue ?? candidate.sourceVenue ?? candidate.venueHints?.[0];
    const symbol = candidate.normalizedSymbol ?? candidate.rawSymbol;
    const assessBtn = (venue && symbol)
        ? `<button class="button" type="button" data-action="assess-candidate-liquidity" data-id="${candidate.id}" data-venue="${escapeHtml(venue)}" data-symbol="${escapeHtml(symbol)}">${t("liquidity_assess_button")}</button>`
        : "";
    if (!liquidity) {
        return section(t("liquidity_section_title"), `
            ${emptyState(t("liquidity_no_assessment"), t("liquidity_no_assessment_detail"))}
            <div class="actions">${assessBtn}</div>
        `);
    }
    const warning = liquidity.score === "UNTRADABLE"
        ? `<div class="banner">${t("liquidity_warning_untradable")}</div>`
        : liquidity.score === "THIN"
            ? `<div class="banner" style="border-color:rgba(255,190,60,0.22);background:linear-gradient(180deg,rgba(58,44,10,0.96),rgba(36,27,6,0.94))">${t("liquidity_warning_thin")}</div>`
            : "";
    return section(t("liquidity_section_title"), `
        ${warning}
        <div class="meta-grid">
            ${metaRow(t("liquidity_score"), formatBadge("liquidity", liquidity.score))}
            ${metaRow(t("liquidity_recommended_max"), liquidity.recommendedMaxOrderNotional != null ? `${formatDecimal(liquidity.recommendedMaxOrderNotional, 2)} USD` : "—")}
            ${metaRow(t("liquidity_round_trip_safe"), liquidity.roundTripSafeNotional != null ? `${formatDecimal(liquidity.roundTripSafeNotional, 2)} USD` : "—")}
            ${metaRow(t("liquidity_spread_bps"), liquidity.spreadBps != null ? `${formatDecimal(liquidity.spreadBps, 2)} bps` : "—")}
            ${metaRow(t("liquidity_sampled_at"), formatInstant(liquidity.sampledAt))}
        </div>
        <div class="actions">${assessBtn}</div>
    `);
}

function buildAiAdvisorSection(candidate, aiEnabled) {
    if (!aiEnabled) {
        return section(t("ai_advisor_title"), `
            <div class="action-card">
                <p class="helper-text muted">${t("ai_disabled_hint")}</p>
            </div>
        `);
    }
    const ai = candidate.aiAdvice;
    if (!ai) {
        return section(t("ai_advisor_title"), `
            <div class="action-card">
                <div class="actions">
                    <button class="button secondary" type="button" data-action="analyze-candidate" data-id="${candidate.id}">${t("ai_analyze_button")}</button>
                </div>
            </div>
        `);
    }
    return section(t("ai_advisor_title"), `
        <div class="action-card">
            <div class="detail-grid">
                <div class="inline-kv"><span class="muted">${t("ai_confidence")}</span><span>${formatAiBadge(ai)}</span></div>
                <div class="inline-kv"><span class="muted">${t("ai_reasoning")}</span><span class="meta-value">${escapeHtml(ai.reasoning ?? "—")}</span></div>
                <div class="inline-kv"><span class="muted">${t("ai_model")}</span><span>${escapeHtml(ai.modelUsed ?? "—")}</span></div>
                <div class="inline-kv"><span class="muted">${t("ai_analyzed_at")}</span><span>${formatInstant(ai.analyzedAt)}</span></div>
            </div>
            <div class="actions">
                <button class="button secondary" type="button" data-action="analyze-candidate" data-id="${candidate.id}">${t("ai_reanalyze")}</button>
            </div>
        </div>
    `);
}

function buildEnrichmentTimelineSection(candidate, liquidity) {
    const layers = [];

    // Layer 1 — Base Signal
    layers.push({
        _order: 0,
        name: t('layer.base') || 'Базовый сигнал',
        timestamp: candidate.detectedAt ?? null,
        status: candidate.status === 'NORMALIZED' ? 'ok'
              : candidate.status === 'FAILED'     ? 'blocked'
              : 'warn',
        decorator: 'FUNDING_API',
        details: `Источник: ${escapeHtml(candidate.sourceVenue ?? candidate.sourceType)} · ${escapeHtml(candidate.rawSymbol)}`
    });

    // Layer 2 — Normalization
    const normHasTimestamp = candidate.status === 'NORMALIZED' || candidate.status === 'EVENT_CREATED';
    layers.push({
        _order: 1,
        name: 'Нормализация',
        timestamp: normHasTimestamp ? (candidate.updatedAt ?? null) : null,
        status: (candidate.status === 'NORMALIZED' || candidate.status === 'EVENT_CREATED') ? 'ok'
              : candidate.status === 'FAILED' ? 'blocked'
              : 'missing',
        decorator: 'NormalizationService',
        details: escapeHtml(candidate.normalizationFailureReason ?? '')
    });

    // Layer 3 — Liquidity
    layers.push({
        _order: 2,
        name: t('layer.liquidity') || 'Ликвидность',
        timestamp: liquidity?.sampledAt ?? null,
        status: !liquidity ? 'missing'
              : (liquidity.score === 'EXCELLENT' || liquidity.score === 'GOOD' || liquidity.score === 'MEDIUM') ? 'ok'
              : liquidity.score === 'THIN' ? 'warn'
              : 'blocked',
        decorator: 'LiquidityAssessmentService',
        details: liquidity ? `Score: ${escapeHtml(liquidity.score)}` : ''
    });

    // Layer 4 — AI Advice
    layers.push({
        _order: 3,
        name: t('layer.ai') || 'ИИ-советник',
        timestamp: candidate.aiAdvice?.analyzedAt ?? null,
        status: !candidate.aiAdvice ? 'missing'
              : candidate.aiAdvice.recommendation === 'GO'   ? 'ok'
              : candidate.aiAdvice.recommendation === 'WATCH' ? 'warn'
              : 'blocked',
        decorator: candidate.aiAdvice?.modelUsed ?? 'AiSignalAdvisorService',
        details: candidate.aiAdvice
            ? `${escapeHtml(candidate.aiAdvice.recommendation)} · ${Math.round(candidate.aiAdvice.confidence * 100)}%`
            : ''
    });

    // Sort by timestamp (nulls last), preserving logical order for equal/null timestamps
    layers.sort(function(a, b) {
        if (a.timestamp === null && b.timestamp === null) return a._order - b._order;
        if (a.timestamp === null) return 1;
        if (b.timestamp === null) return -1;
        const diff = new Date(a.timestamp) - new Date(b.timestamp);
        return diff !== 0 ? diff : a._order - b._order;
    });

    return section(t('action.view_enrichment_history') || 'История обогащения', renderEnrichmentTimeline(layers));
}

export function buildCandidateDrawerContent(candidate, aiEnabled, liquidity) {
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
        ${buildCandidateLiquiditySection(liquidity, candidate)}
        ${buildAiAdvisorSection(candidate, aiEnabled)}
        ${buildEnrichmentTimelineSection(candidate, liquidity)}
        ${buildApproveSection(candidate)}
        ${buildRejectSection(candidate)}
        ${candidate.status !== "DELETED" ? buildDeleteCandidateSection(candidate) : ""}
    `;
}

export async function openCandidateDetail({ id, nodes, showError }) {
    try {
        const [candidate, aiStatus] = await Promise.all([
            api.getCandidate(id),
            api.getAiStatus().catch(() => ({ enabled: false }))
        ]);
        const liquidity = await api.getCandidateLiquidity(id);
        nodes.modalType.textContent = t("candidate_modal_type");
        nodes.modalTitle.textContent = candidate.normalizedSymbol ?? candidate.rawSymbol;
        nodes.modalContent.innerHTML = buildCandidateDrawerContent(candidate, aiStatus.enabled, liquidity);
        openModal(nodes);
    } catch (error) {
        showError(error.message);
    }
}
