import { setLang } from "../../main/resources/static/i18n.js";
setLang("en");

import assert from "node:assert/strict";
import test from "node:test";

import {
    dashboardDevToolsMarkup,
    dashboardSummaryMarkup
} from "../../main/resources/static/app/screens/dashboard.js";

test("dashboard summary keeps current operator snapshot messaging", () => {
    const markup = dashboardSummaryMarkup({
        pendingCandidates: 4,
        fundingEvents: 7,
        discoveredEvents: 2,
        armedTrades: 3,
        globalAccessMode: "testnet",
        activeVenues: 5,
        version: "2.0.0"
    });

    assert.match(markup, /Signal Queue/);
    assert.match(markup, /Funding Events/);
    assert.match(markup, /Prepared Trades/);
    assert.match(markup, /Access mode/);
    assert.match(markup, /TESTNET/);
});

test("dashboard dev tools shows runtime error state", () => {
    const markup = dashboardDevToolsMarkup(null, "engine unavailable");

    assert.match(markup, /Runtime control temporarily unavailable/);
    assert.match(markup, /engine unavailable/);
    assert.match(markup, /Run once · dev/);
});

test("dashboard dev tools shows runtime telemetry state", () => {
    const markup = dashboardDevToolsMarkup({
        executionLoopEnabled: true,
        executionLoopIntervalMs: 1500,
        runtimeUpdatedAt: "2030-01-01T00:00:00.000Z",
        lastRunFinishedAt: "2030-01-01T00:00:30.000Z",
        lastExecutionRunDurationMs: 164,
        lastAttemptsSubmitted: 9,
        lastAttemptsSkipped: 3,
        lastPlansScanned: 12,
        lastForcedRunFinishedAt: "2030-01-01T00:00:31.000Z",
        lastForcedRunDurationMs: 180,
        lastForcedAttemptsSubmitted: 9,
        lastForcedAttemptsSkipped: 3,
        lastForcedPlansScanned: 12,
        minimumExecutionLoopIntervalMs: 500
    }, null);

    assert.match(markup, /Execution loop/);
    assert.match(markup, /Apply runtime/);
    assert.match(markup, /Run once · dev/);
    assert.match(markup, /1500/);
});
