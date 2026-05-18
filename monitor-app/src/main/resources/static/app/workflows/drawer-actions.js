import { api } from "../../api.js";
import { numberOrNull, resetDrawer, toIsoOrNull } from "../shared.js";
import { renderDevTestRunCreated } from "./dev-test-run.js";
import { t } from "../../i18n.js";

export function createDrawerActionHandler({
    nodes,
    refreshCurrentScreen,
    showSuccess,
    showError,
    switchScreen,
    openCandidateDetail,
    openEventDetail,
    openTradeDetail,
    openVenueDetail
}) {
    return async function handleDrawerAction(event) {
        if (event.type === "submit") {
            const form = event.target.closest("form.drawer-form");
            if (!form) {
                return;
            }
            event.preventDefault();
            const data = new FormData(form);
            const action = form.dataset.action;
            try {
                if (action === "approve-candidate") {
                    await api.approveCandidate(form.dataset.id, {
                        venue: data.get("venue") || null,
                        symbol: data.get("symbol") || null,
                        fundingTime: toIsoOrNull(data.get("fundingTime")),
                        fundingRatePct: numberOrNull(data.get("fundingRatePct")),
                        reviewNotes: data.get("reviewNotes") || null
                    });
                    showSuccess(t("action_candidate_approved"));
                    await Promise.all([refreshCurrentScreen(), openCandidateDetail(form.dataset.id)]);
                    switchScreen("events");
                    return;
                }
                if (action === "reject-candidate") {
                    await api.rejectCandidate(form.dataset.id, {
                        reviewNotes: data.get("reviewNotes") || null
                    });
                    showSuccess(t("action_candidate_rejected"));
                    await Promise.all([refreshCurrentScreen(), openCandidateDetail(form.dataset.id)]);
                    return;
                }
                if (action === "delete-candidate") {
                    if (!window.confirm(t("action_delete_confirm"))) {
                        return;
                    }
                    await api.deleteCandidate(form.dataset.id, data.get("deleteNote") || null);
                    showSuccess(t("action_candidate_deleted"));
                    await refreshCurrentScreen();
                    resetDrawer(nodes, t("action_drawer_hint"));
                    return;
                }
                if (action === "arm-event") {
                    await api.armFundingEvent(form.dataset.id, {
                        notionalUsd: numberOrNull(data.get("notionalUsd")),
                        intendedSide: "SHORT",
                        plannedEntryAt: toIsoOrNull(data.get("plannedEntryAt")),
                        plannedExitAt: toIsoOrNull(data.get("plannedExitAt")),
                        entryAttemptCount: numberOrNull(data.get("entryAttemptCount")),
                        entrySpacingMs: numberOrNull(data.get("entrySpacingMs")),
                        manualLatencyAdjustmentMs: numberOrNull(data.get("manualLatencyAdjustmentMs")),
                        stopLossUsd: numberOrNull(data.get("stopLossUsd")),
                        takeProfitUsd: numberOrNull(data.get("takeProfitUsd")),
                        notes: data.get("notes") || null
                    });
                    showSuccess(t("action_trade_created"));
                    await Promise.all([refreshCurrentScreen(), openEventDetail(form.dataset.id)]);
                    switchScreen("trades");
                    return;
                }
                if (action === "upsert-credential") {
                    await api.upsertCredential(form.dataset.venue, form.dataset.mode, {
                        apiKey: data.get("apiKey"),
                        secretKey: data.get("secretKey"),
                        passphrase: data.get("passphrase") || null
                    });
                    showSuccess(`${t("action_keys_saved")} ${form.dataset.venue}.`);
                    await Promise.all([refreshCurrentScreen(), openVenueDetail(form.dataset.venue)]);
                    return;
                }
                if (action === "set-venue-default-latency") {
                    const ms = numberOrNull(data.get("defaultManualLatencyAdjustmentMs"));
                    await api.setVenueDefaultLatency(form.dataset.venue, ms);
                    showSuccess(`${t("venue_default_latency_save")} → ${form.dataset.venue}: ${ms ?? "cleared"} ms`);
                    await openVenueDetail(form.dataset.venue);
                    return;
                }
                if (action === "create-dev-test-run") {
                    const run = await api.createDevTestRun({
                        venue: data.get("venue"),
                        symbol: data.get("symbol"),
                        notionalUsd: numberOrNull(data.get("notionalUsd"))
                    });
                    const options = await api.getDevTestRunOptions();
                    renderDevTestRunCreated({ nodes, options, run });
                    await refreshCurrentScreen();
                    showSuccess(`${t("action_dev_test_created")} ${run.venue} ${run.symbol} #${run.armedTradeId}.`);
                    return;
                }
            } catch (error) {
                showError(error.message);
            }
            return;
        }

        const deleteCredentialButton = event.target.closest("[data-action='delete-credential']");
        if (deleteCredentialButton) {
            if (!window.confirm(`${t("action_delete_keys_confirm")} ${deleteCredentialButton.dataset.venue}?`)) return;
            try {
                await api.deleteCredential(deleteCredentialButton.dataset.venue, deleteCredentialButton.dataset.mode);
                showSuccess(`${t("action_keys_deleted")} ${deleteCredentialButton.dataset.venue}.`);
                await Promise.all([refreshCurrentScreen(), openVenueDetail(deleteCredentialButton.dataset.venue)]);
            } catch (error) {
                showError(error.message);
            }
            return;
        }

        const syncButton = event.target.closest("[data-action='sync-venue']");
        if (syncButton) {
            try {
                await api.syncVenue(syncButton.dataset.venue);
                showSuccess(`${t("dev_venue_label")} ${syncButton.dataset.venue} ${t("action_venue_synced")}`);
                await Promise.all([refreshCurrentScreen(), openVenueDetail(syncButton.dataset.venue)]);
            } catch (error) {
                showError(error.message);
            }
            return;
        }

        const checkButton = event.target.closest("[data-action='check-venue']");
        if (checkButton) {
            try {
                await api.checkVenueCredentials(checkButton.dataset.venue);
                showSuccess(`${t("action_credential_checked")} ${checkButton.dataset.venue}.`);
                await Promise.all([refreshCurrentScreen(), openVenueDetail(checkButton.dataset.venue)]);
            } catch (error) {
                showError(error.message);
            }
            return;
        }

        const devRunButton = event.target.closest("[data-action='run-dev-test-entry'], [data-action='run-dev-test-exit']");
        if (devRunButton) {
            const productionConfirm = nodes.modalContent.querySelector("[data-production-confirm]")?.value ?? null;
            const payload = productionConfirm ? { productionConfirm } : {};
            const run = {
                armedTradeId: devRunButton.dataset.armedTradeId,
                venue: devRunButton.dataset.venue,
                symbol: devRunButton.dataset.symbol,
                mode: devRunButton.dataset.mode,
                notionalUsd: devRunButton.dataset.notionalUsd,
                status: "ARMED"
            };
            try {
                const execution = devRunButton.dataset.action === "run-dev-test-entry"
                    ? await api.runDevTestEntry(run.armedTradeId, payload)
                    : await api.runDevTestExit(run.armedTradeId, payload);
                const options = await api.getDevTestRunOptions();
                renderDevTestRunCreated({ nodes, options, run, execution });
                await refreshCurrentScreen();
                showSuccess(`${execution.phase} completed: ${t("dev_submitted")} ${execution.execution.attemptsSubmitted}, ${t("dev_skipped")} ${execution.execution.attemptsSkipped}.`);
            } catch (error) {
                showError(error.message);
            }
            return;
        }

        const probeButton = event.target.closest("[data-action='probe-venue-latency']");
        if (probeButton) {
            const venue = probeButton.dataset.venue;
            const inlineEl = probeButton.closest(".actions")?.parentElement?.querySelector("#venue-probe-inline");
            if (inlineEl) inlineEl.textContent = "…";
            try {
                const result = await api.probeVenueLatency(venue);
                const msg = `${result.durationMs} ms`;
                if (inlineEl) inlineEl.textContent = msg;
                const p50El = probeButton.closest(".modal-content, .drawer-content")?.querySelector("#venue-probe-result");
                if (p50El) p50El.textContent = msg;
            } catch (error) {
                if (inlineEl) inlineEl.textContent = `✗ ${error.message}`;
                showError(error.message);
            }
            return;
        }

        const openEntityButton = event.target.closest("[data-open-candidate],[data-open-event],[data-open-trade],[data-open-venue]");
        if (openEntityButton) {
            if (openEntityButton.dataset.openCandidate) openCandidateDetail(openEntityButton.dataset.openCandidate);
            if (openEntityButton.dataset.openEvent) openEventDetail(openEntityButton.dataset.openEvent);
            if (openEntityButton.dataset.openTrade) openTradeDetail(openEntityButton.dataset.openTrade);
            if (openEntityButton.dataset.openVenue) openVenueDetail(openEntityButton.dataset.openVenue);
        }
    };
}
