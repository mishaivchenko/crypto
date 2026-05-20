import { setLang } from "../../main/resources/static/i18n.js";
setLang("en");

import assert from "node:assert/strict";
import test from "node:test";

import { candidatesListMarkup } from "../../main/resources/static/app/screens/candidates.js";
import { eventsListMarkup } from "../../main/resources/static/app/screens/events.js";
import { tradesListMarkup } from "../../main/resources/static/app/screens/trades.js";
import { historyListMarkup } from "../../main/resources/static/app/screens/history.js";
import { venuesListMarkup } from "../../main/resources/static/app/screens/venues.js";

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

    assert.match(compact, /WET\/USDT/);
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
    assert.match(loaded, /Registry ready/);
});
