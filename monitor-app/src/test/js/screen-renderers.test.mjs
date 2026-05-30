import { setLang } from "../../main/resources/static/i18n.js";
setLang("en");

import assert from "node:assert/strict";
import test from "node:test";

import { candidatesListMarkup } from "../../main/resources/static/app/screens/candidates.js";
import { eventsListMarkup } from "../../main/resources/static/app/screens/events.js";
import { tradesListMarkup } from "../../main/resources/static/app/screens/trades.js";
import { historyListMarkup } from "../../main/resources/static/app/screens/history.js";
import { venuesListMarkup } from "../../main/resources/static/app/screens/venues.js";
import { buildEventDrawerContent } from "../../main/resources/static/app/workflows/event-detail.js";

test("candidates screen renders empty and loaded states", () => {
    assert.match(candidatesListMarkup({ content: [] }), /Signal Queue is empty/);

    const compact = candidatesListMarkup({
        content: [
            {
                id: 12,
                normalizedSymbol: "WET/USDT",
                rawSymbol: "WETUSDT",
                sourceVenue: "gate",
                sourceType: "FUNDING_API",
                status: "NORMALIZED",
                detectedAt: "2030-01-01T00:00:00.000Z"
            }
        ]
    });

    assert.match(compact, /analyzing|анализируются/);
    assert.doesNotMatch(compact, /WET\/USDT/);
    assert.doesNotMatch(compact, /quick-approve-candidate/);

    const full = candidatesListMarkup({
        content: [
            {
                id: 13,
                normalizedSymbol: "BTC/USDT",
                rawSymbol: "BTCUSDT",
                sourceVenue: "gate",
                sourceType: "FUNDING_API",
                status: "NORMALIZED",
                sourceFundingTime: "2030-01-01T08:00:00.000Z",
                sourceFundingRatePct: 0.025,
                detectedAt: "2030-01-01T00:00:00.000Z",
                aiAdvice: { recommendation: "GO", confidence: 0.85, reasoning: "Good rate", modelUsed: "deepseek-chat", analyzedAt: "2030-01-01T00:01:00.000Z" }
            }
        ]
    }, {
        liquidityMap: {
            13: { score: "GOOD", recommendedMaxOrderNotional: 5000, spreadBps: 3.5 }
        }
    });

    assert.match(full, /BTC\/USDT/);
    assert.match(full, /quick-approve-candidate/);
    assert.match(full, /quick-reject-candidate/);
});

test("events and trades screens keep empty-state guidance", () => {
    assert.match(eventsListMarkup({ content: [] }), /No Funding Events yet/);
    assert.match(tradesListMarkup([]), /No Prepared Trades yet/);
});

test("history screen renders filtered count and rows", () => {
    const markup = historyListMarkup({
        trades: [
            {
                id: 42,
                venue: "gate",
                symbol: "WET/USDT",
                fundingTime: "2030-01-01T00:00:00.000Z",
                intendedSide: "SHORT",
                plannedEntryAt: "2029-12-31T23:59:50.000Z",
                plannedExitAt: "2030-01-01T00:00:30.000Z",
                entryAttemptCount: 3,
                entrySpacingMs: 150,
                effectiveEntryLatencyMs: 50,
                manualLatencyAdjustmentMs: 10,
                state: "ARMED"
            }
        ],
        attemptsByTrade: {
            42: [
                { attemptNumber: 1, status: "FAILED", createdAt: "2030-01-01T00:00:01.000Z" }
            ]
        },
        filters: {}
    });

    assert.equal(markup.countLabel, "1 / 1 trades");
    assert.match(markup.listMarkup, /WET\/USDT/);
    assert.match(markup.listMarkup, /Attempts failed/);
});

test("event drawer content renders decorator sections conditionally", () => {
    const event = {
        id: 7,
        venue: "gate",
        symbol: "BTC/USDT",
        fundingTime: "2030-01-01T08:00:00.000Z",
        fundingRatePct: 0.025,
        status: "DISCOVERED",
        sourceType: "FUNDING_API",
        signalCandidateId: null,
        armedTradeId: null
    };

    const discovered = buildEventDrawerContent({ event, journal: [] });
    assert.match(discovered, /Event snapshot/);
    assert.match(discovered, /Arm Prepared Trade/);
    assert.doesNotMatch(discovered, /Trade Parameters/);
    assert.doesNotMatch(discovered, /Latency Chain/);
    assert.doesNotMatch(discovered, /Outcome/);

    const trade = {
        id: 10,
        state: "ARMED",
        venue: "gate",
        symbol: "BTC/USDT",
        intendedSide: "SHORT",
        notionalUsd: 50,
        plannedEntryAt: "2030-01-01T07:59:15.000Z",
        plannedExitAt: "2030-01-01T08:01:30.000Z",
        entryAttemptCount: 3,
        entrySpacingMs: 150,
        manualLatencyAdjustmentMs: 10,
        measuredEntryLatencyMs: 45,
        effectiveEntryLatencyMs: 55,
        warmupP50Ms: 40,
        warmupP95Ms: 60,
        entryLeadMs: 55,
        exitLeadMs: 200,
        armedAt: "2030-01-01T07:00:00.000Z"
    };

    const armed = buildEventDrawerContent({
        event: { ...event, status: "ARMED", armedTradeId: 10 },
        journal: [],
        trade
    });
    assert.match(armed, /Trade Parameters/);
    assert.match(armed, /Latency Chain/);
    assert.doesNotMatch(armed, /Arm Prepared Trade/);
    assert.doesNotMatch(armed, /Outcome/);

    const outcome = { netPnlUsd: 1.23, grossPnlUsd: 1.50, feesUsd: 0.27, outcomeCode: "PROFIT", evaluatedAt: "2030-01-01T08:02:00.000Z" };
    const position = { entryPrice: 50000, exitPrice: 50100, quantity: 0.001, openedAt: "2030-01-01T07:59:30.000Z", closedAt: "2030-01-01T08:01:00.000Z" };
    const attempts = [{ attemptNumber: 1, status: "FILLED", symbol: "BTCUSDT", side: "Sell", triggerAt: "2030-01-01T07:59:15.000Z" }];

    const closed = buildEventDrawerContent({
        event: { ...event, status: "CANCELLED", armedTradeId: 10 },
        journal: [],
        trade: { ...trade, state: "CLOSED" },
        attempts,
        outcome,
        position
    });
    assert.match(closed, /Trade Parameters/);
    assert.match(closed, /Latency Chain/);
    assert.match(closed, /Execution Attempts/);
    assert.match(closed, /Position/);
    assert.match(closed, /Outcome/);
    assert.match(closed, /\+1\.23 USD/);
});

test("venues screen renders empty and loaded states", () => {
    assert.match(venuesListMarkup([]), /Venue Access is empty/);

    const loaded = venuesListMarkup([
        {
            venue: "bybit",
            mode: "testnet",
            activeInstrumentCount: 10,
            credentialsConfigured: true,
            connectionStatus: "CONNECTED",
            lastSyncedAt: "2030-01-01T00:00:00.000Z",
            averageRequestTimeMs: 42,
            requests: 7
        }
    ]);

    assert.match(loaded, /bybit/);
    assert.match(loaded, /Ready/);
});
