import assert from "node:assert/strict";
import test from "node:test";

import {
    buildAttemptPlan,
    deriveHistoryStage,
    deriveTradeHealth,
    filterHistoryTrades,
    historyTradeRow,
    tradeHistoryDetailMarkup
} from "../../main/resources/static/history.js";

const baseTrade = {
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
    notes: "burst entry"
};

test("buildAttemptPlan creates latency-adjusted burst attempts", () => {
    const plan = buildAttemptPlan(baseTrade);

    assert.equal(plan.length, 3);
    assert.equal(plan[0].attemptNumber, 1);
    assert.equal(plan[0].targetEntryAt, "2029-12-31T23:59:50.000Z");
    assert.equal(plan[0].triggerAt, "2029-12-31T23:59:49.950Z");
    assert.equal(plan[1].targetEntryAt, "2029-12-31T23:59:50.150Z");
    assert.equal(plan[2].offsetMs, 300);
});

test("deriveTradeHealth prioritizes failed and manual override states", () => {
    assert.deepEqual(deriveTradeHealth({ ...baseTrade, state: "FAILED" }), {
        label: "failed",
        tone: "bad",
        reason: "Trade state failed"
    });

    assert.equal(deriveTradeHealth(baseTrade).label, "manual override");
    assert.equal(deriveTradeHealth({ ...baseTrade, manualLatencyAdjustmentMs: 0 }).label, "burst plan");
});

test("deriveHistoryStage marks failed recorded attempts ahead of raw armed state", () => {
    const stage = deriveHistoryStage(baseTrade, { status: "EXPIRED" }, [
        { attemptNumber: 1, status: "FAILED", createdAt: "2030-01-01T00:00:01.000Z" },
        { attemptNumber: 2, status: "FAILED", createdAt: "2030-01-01T00:00:01.100Z" }
    ]);

    assert.equal(stage.code, "ATTEMPTS_FAILED");
});

test("filterHistoryTrades applies operator filters", () => {
    const trades = [
        baseTrade,
        { ...baseTrade, id: 43, venue: "bybit", symbol: "NOM/USDT", state: "FAILED", manualLatencyAdjustmentMs: 0 },
        { ...baseTrade, id: 44, venue: "gate", symbol: "EDGE/USDT", state: "CLOSED", manualLatencyAdjustmentMs: 0 }
    ];
    const attemptsByTrade = {
        42: [
            { attemptNumber: 1, status: "FAILED", createdAt: "2030-01-01T00:00:01.000Z" }
        ]
    };

    assert.deepEqual(filterHistoryTrades(trades, { venue: "gate" }).map((trade) => trade.id), [42, 44]);
    assert.deepEqual(filterHistoryTrades(trades, { onlyFailed: true }, attemptsByTrade).map((trade) => trade.id), [42, 43]);
    assert.deepEqual(filterHistoryTrades(trades, { onlyManual: true }).map((trade) => trade.id), [42]);
    assert.deepEqual(filterHistoryTrades(trades, { symbol: "nom" }).map((trade) => trade.id), [43]);
});

test("history row exposes key trading context", () => {
    const row = historyTradeRow(baseTrade, [
        { attemptNumber: 1, status: "FAILED", createdAt: "2030-01-01T00:00:01.000Z" }
    ]);

    assert.match(row, /WET\/USDT/);
    assert.match(row, /gate/);
    assert.match(row, /3 attempts \/ 150 мс/);
    assert.match(row, /Attempts failed/);
    assert.match(row, /manual \+10 мс/);
});

test("detail markup tells the full source-to-outcome story", () => {
    const markup = tradeHistoryDetailMarkup({
        trade: baseTrade,
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
            detectedAt: "2029-12-31T23:50:00.000Z",
            reviewNotes: "valid"
        },
        journal: [],
        attempts: [
            {
                attemptNumber: 1,
                status: "FAILED",
                symbol: "WET/USDT",
                failureReason: "Missing credentials",
                triggerAt: "2029-12-31T23:59:50.000Z",
                createdAt: "2030-01-01T00:00:01.000Z"
            }
        ]
    });

    assert.match(markup, /1\. Source/);
    assert.match(markup, /2\. Event/);
    assert.match(markup, /3\. Plan/);
    assert.match(markup, /4\. Attempts/);
    assert.match(markup, /5\. Position/);
    assert.match(markup, /6\. Outcome/);
    assert.match(markup, /History stage/);
    assert.match(markup, /Attempts failed/);
    assert.match(markup, /Missing credentials/);
});
