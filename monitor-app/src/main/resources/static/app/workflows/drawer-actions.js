import { api } from "../../api.js";
import { numberOrNull, resetDrawer, toIsoOrNull } from "../shared.js";
import { renderDevTestRunCreated } from "./dev-test-run.js";

export function createDrawerActionHandler({
    nodes,
    refreshCurrentScreen,
    showSuccess,
    showError,
    openCandidateDetail,
    openEventDetail,
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
                    showSuccess("Кандидат переведён в событие фандинга.");
                    await Promise.all([refreshCurrentScreen(), openCandidateDetail(form.dataset.id)]);
                    return;
                }
                if (action === "reject-candidate") {
                    await api.rejectCandidate(form.dataset.id, {
                        reviewNotes: data.get("reviewNotes") || null
                    });
                    showSuccess("Candidate rejected.");
                    await Promise.all([refreshCurrentScreen(), openCandidateDetail(form.dataset.id)]);
                    return;
                }
                if (action === "delete-candidate") {
                    if (!window.confirm("Удалить candidate и очистить связанный pipeline?")) {
                        return;
                    }
                    await api.deleteCandidate(form.dataset.id, data.get("deleteNote") || null);
                    showSuccess("Candidate deleted and pipeline cleaned.");
                    await refreshCurrentScreen();
                    resetDrawer(nodes, "Открой signal, Funding Event, Prepared Trade или venue, чтобы посмотреть детали и следующее действие.");
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
                        notes: data.get("notes") || null
                    });
                    showSuccess("Prepared Trade created.");
                    await Promise.all([refreshCurrentScreen(), openEventDetail(form.dataset.id)]);
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
                    showSuccess(`DEV_TEST created: ${run.venue} ${run.symbol} #${run.armedTradeId}.`);
                    return;
                }
            } catch (error) {
                showError(error.message);
            }
            return;
        }

        const syncButton = event.target.closest("[data-action='sync-venue']");
        if (syncButton) {
            try {
                await api.syncVenue(syncButton.dataset.venue);
                showSuccess(`Venue ${syncButton.dataset.venue} synced.`);
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
                showSuccess(`Credential check completed for ${checkButton.dataset.venue}.`);
                await Promise.all([refreshCurrentScreen(), openVenueDetail(checkButton.dataset.venue)]);
            } catch (error) {
                showError(error.message);
            }
        }

        const devRunButton = event.target.closest("[data-action='run-dev-test-entry'], [data-action='run-dev-test-exit']");
        if (devRunButton) {
            const productionConfirm = nodes.drawerContent.querySelector("[data-production-confirm]")?.value ?? null;
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
                showSuccess(`${execution.phase} completed: submitted ${execution.execution.attemptsSubmitted}, skipped ${execution.execution.attemptsSkipped}.`);
            } catch (error) {
                showError(error.message);
            }
        }
    };
}
