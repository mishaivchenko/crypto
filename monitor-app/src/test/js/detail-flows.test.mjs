import { setLang } from "../../main/resources/static/i18n.js";
setLang("en");

import assert from "node:assert/strict";
import test from "node:test";

import { buildCandidateDrawerContent } from "../../main/resources/static/app/workflows/candidate-detail.js";
import { buildEventDrawerContent } from "../../main/resources/static/app/workflows/event-detail.js";
import { buildTradeDrawerContent } from "../../main/resources/static/app/workflows/trade-detail.js";
import { buildHistoryTradeDrawerContent } from "../../main/resources/static/app/workflows/history-detail.js";
import { buildVenueDrawerContent } from "../../main/resources/static/app/workflows/venue-detail.js";

test("candidate drawer exposes review actions", () => {
    const markup = buildCandidateDrawerContent({
        id: 3,
        status: "NORMALIZED",
        sourceType: "FUNDING_API",
        sourceVenue: "gate",
        rawSymbol: "WETUSDT",
        normalizedSymbol: "WET/USDT",
        venueHints: ["gate"],
        detectedAt: "2029-12-31T23:50:00.000Z",
        suggestedVenue: "gate",
        suggestedFundingTime: "2030-01-01T00:00:00.000Z",
        suggestedFundingRatePct: -0.0125
    });

    assert.match(markup, /Approve to Funding Event/);
    assert.match(markup, /Reject candidate/);
    assert.match(markup, /Delete candidate/);
});

test("event drawer keeps arm-trade workflow", () => {
    const markup = buildEventDrawerContent({
        event: {
            id: 7,
            venue: "gate",
            symbol: "WET/USDT",
            fundingTime: "2030-01-01T00:00:00.000Z",
            fundingRatePct: -0.0125,
            status: "DISCOVERED",
            sourceType: "FUNDING_API",
            signalCandidateId: 3
        },
        journal: []
    });

    assert.match(markup, /Create Prepared Trade/);
    assert.match(markup, /Funding time/);
    assert.match(markup, /Journal/);
});

test("trade and history drawers keep source-to-outcome narrative", () => {
    const tradeMarkup = buildTradeDrawerContent({
        trade: {
            id: 42,
            fundingEventId: 7,
            signalCandidateId: 3,
            venue: "gate",
            symbol: "WET/USDT",
            fundingTime: "2030-01-01T00:00:00.000Z",
            notionalUsd: 25,
            intendedSide: "SHORT",
            plannedEntryAt: "2029-12-31T23:59:50.000Z",
            plannedExitAt: "2030-01-01T00:00:30.000Z",
            entryAttemptCount: 3,
            entrySpacingMs: 150,
            measuredEntryLatencyMs: 40,
            manualLatencyAdjustmentMs: 10,
            effectiveEntryLatencyMs: 50,
            armSource: "EVENT_API",
            state: "ARMED",
            armedAt: "2029-12-31T23:30:00.000Z"
        },
        journal: [],
        attempts: []
    });
    const historyMarkup = buildHistoryTradeDrawerContent({
        trade: {
            id: 42,
            fundingEventId: 7,
            signalCandidateId: 3,
            venue: "gate",
            symbol: "WET/USDT",
            fundingTime: "2030-01-01T00:00:00.000Z",
            notionalUsd: 25,
            intendedSide: "SHORT",
            plannedEntryAt: "2029-12-31T23:59:50.000Z",
            plannedExitAt: "2030-01-01T00:00:30.000Z",
            entryAttemptCount: 3,
            entrySpacingMs: 150,
            measuredEntryLatencyMs: 40,
            manualLatencyAdjustmentMs: 10,
            effectiveEntryLatencyMs: 50,
            armSource: "EVENT_API",
            state: "ARMED"
        },
        event: {
            id: 7,
            venue: "gate",
            symbol: "WET/USDT",
            fundingTime: "2030-01-01T00:00:00.000Z",
            fundingRatePct: -0.0125,
            status: "ARMED",
            sourceType: "FUNDING_API"
        },
        candidate: {
            id: 3,
            sourceType: "FUNDING_API",
            rawSymbol: "WETUSDT",
            normalizedSymbol: "WET/USDT",
            detectedAt: "2029-12-31T23:50:00.000Z"
        },
        journal: [],
        attempts: []
    });

    assert.match(tradeMarkup, /Base Signal/);
    assert.match(tradeMarkup, /Latency Chain/);
    assert.match(historyMarkup, /1\. Source/);
    assert.match(historyMarkup, /empty-state compact/);
});

test("venue drawer keeps actions and diagnostics", () => {
    const markup = buildVenueDrawerContent({
        venue: {
            venue: "bybit",
            configuredMode: "testnet",
            credentialsConfigured: true,
            connectionStatus: "CONNECTED",
            connectionMessage: "ready",
            activeInstrumentCount: 10,
            lastSyncedAt: "2030-01-01T00:00:00.000Z",
            lastCheckedAt: "2030-01-01T00:00:00.000Z"
        },
        instruments: [],
        timings: []
    });

    assert.match(markup, /Check keys/);
    assert.match(markup, /Sync instruments/);
    assert.match(markup, /Venue profile/);
});
