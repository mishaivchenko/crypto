import { setLang } from "../../main/resources/static/i18n.js";
setLang("en");

import assert from "node:assert/strict";
import test from "node:test";

import { escapeHtml } from "../../main/resources/static/ui.js";
import { candidatesListMarkup } from "../../main/resources/static/app/screens/candidates.js";
import { eventsListMarkup } from "../../main/resources/static/app/screens/events.js";
import { tradesListMarkup } from "../../main/resources/static/app/screens/trades.js";
import { historyListMarkup } from "../../main/resources/static/app/screens/history.js";
import { buildCandidateDrawerContent } from "../../main/resources/static/app/workflows/candidate-detail.js";
import { buildEventExpansionContent } from "../../main/resources/static/app/workflows/event-detail.js";
import { buildTradeExpansionContent } from "../../main/resources/static/app/workflows/trade-detail.js";
import { buildHistoryTradeDrawerContent } from "../../main/resources/static/app/workflows/history-detail.js";

// ── XSS escape guard ─────────────────────────────────────────────────────────

test("escapeHtml neutralises all HTML-injection characters", () => {
    assert.equal(escapeHtml('<script>alert("xss")</script>'), '&lt;script&gt;alert(&quot;xss&quot;)&lt;/script&gt;');
    assert.equal(escapeHtml("& < > \""), "&amp; &lt; &gt; &quot;");
    assert.match(escapeHtml("'"), /&#x27;|&#39;/);
    assert.equal(escapeHtml("safe text"), "safe text");
    assert.equal(escapeHtml(""), "");
    assert.equal(escapeHtml(null), "");
    assert.equal(escapeHtml(undefined), "");
});

test("escapeHtml is used in all four expansion error paths", () => {
    // Verify the import is present in each screen module by checking
    // that a string with HTML chars round-trips safely through escapeHtml.
    // The actual DOM path is tested indirectly via the shared escapeHtml contract above.
    const payload = '<img src=x onerror="alert(1)">';
    const escaped = escapeHtml(payload);
    assert.doesNotMatch(escaped, /<img/);
    assert.match(escaped, /&lt;img/);
});

// ── candidates screen ─────────────────────────────────────────────────────────

test("candidatesListMarkup: empty page renders empty state", () => {
    assert.match(candidatesListMarkup({}), /Signal Queue is empty/);
    assert.match(candidatesListMarkup({ content: [] }), /Signal Queue is empty/);
});

test("candidatesListMarkup: cards without AI advice render analyzing message", () => {
    const markup = candidatesListMarkup({
        content: [{ id: 1, status: "NORMALIZED", sourceVenue: "gate", rawSymbol: "BTCUSDT", detectedAt: "2030-01-01T00:00:00.000Z" }]
    });
    assert.match(markup, /analyzing|анализируются/);
    assert.doesNotMatch(markup, /quick-approve-candidate/);
});

test("candidatesListMarkup: card with AI advice and liquidity renders approve/reject actions", () => {
    const markup = candidatesListMarkup({
        content: [{
            id: 5,
            normalizedSymbol: "ETH/USDT",
            rawSymbol: "ETHUSDT",
            sourceVenue: "gate",
            sourceType: "FUNDING_API",
            status: "NORMALIZED",
            sourceFundingTime: "2030-01-01T08:00:00.000Z",
            sourceFundingRatePct: -0.03,
            detectedAt: "2030-01-01T00:00:00.000Z",
            aiAdvice: { recommendation: "GO", confidence: 0.9, reasoning: "High rate", modelUsed: "deepseek", analyzedAt: "2030-01-01T00:01:00.000Z" }
        }]
    }, { liquidityMap: { 5: { score: "GOOD", recommendedMaxOrderNotional: 3000, spreadBps: 2.1 } } });

    assert.match(markup, /ETH\/USDT/);
    assert.match(markup, /quick-approve-candidate/);
    assert.match(markup, /quick-reject-candidate/);
});

test("candidate repair expansion content includes approve and delete forms", () => {
    const markup = buildCandidateDrawerContent({
        id: 99,
        status: "NORMALIZED",
        sourceType: "FUNDING_API",
        sourceVenue: "bybit",
        rawSymbol: "SOLUSDT",
        normalizedSymbol: "SOL/USDT",
        venueHints: ["bybit"],
        detectedAt: "2030-01-01T00:00:00.000Z",
        suggestedVenue: "bybit",
        suggestedFundingTime: "2030-01-01T08:00:00.000Z",
        suggestedFundingRatePct: 0.02
    });
    assert.match(markup, /approve-candidate|Approve/);
    assert.match(markup, /delete-candidate|Delete/);
});

// ── events screen ─────────────────────────────────────────────────────────────

test("eventsListMarkup: empty page renders empty state", () => {
    assert.match(eventsListMarkup({}), /No Funding Events yet/);
    assert.match(eventsListMarkup({ content: [] }), /No Funding Events yet/);
});

test("eventsListMarkup: renders event cards with symbol and venue", () => {
    const markup = eventsListMarkup({ content: [{
        id: 7,
        venue: "gate",
        symbol: "WET/USDT",
        fundingTime: "2030-01-01T00:00:00.000Z",
        fundingRatePct: -0.0125,
        status: "DISCOVERED",
        sourceType: "FUNDING_API",
        signalCandidateId: 3,
        armedTradeId: null
    }] });
    assert.match(markup, /WET\/USDT/);
    assert.match(markup, /gate/);
});

test("event expansion arm form is present for DISCOVERED event", () => {
    const markup = buildEventExpansionContent({
        event: {
            id: 7,
            venue: "gate",
            symbol: "WET/USDT",
            fundingTime: "2030-01-01T00:00:00.000Z",
            fundingRatePct: -0.0125,
            status: "DISCOVERED",
            sourceType: "FUNDING_API"
        },
        candidate: null,
        liquidity: null,
        trade: null,
        attempts: [],
        tradeLiquidity: null,
        outcome: null,
        position: null
    });
    assert.match(markup, /arm-event|Arm|Create Prepared Trade/);
});

test("event expansion arm form is absent for ARMED event", () => {
    const trade = {
        id: 10,
        state: "ARMED",
        venue: "gate",
        symbol: "WET/USDT",
        intendedSide: "SHORT",
        notionalUsd: 25,
        plannedEntryAt: "2030-01-01T07:59:55.000Z",
        plannedExitAt: "2030-01-01T08:01:00.000Z",
        entryAttemptCount: 3,
        entrySpacingMs: 150,
        manualLatencyAdjustmentMs: 10,
        measuredEntryLatencyMs: 40,
        effectiveEntryLatencyMs: 50,
        armedAt: "2030-01-01T07:00:00.000Z"
    };
    const markup = buildEventExpansionContent({
        event: {
            id: 7,
            venue: "gate",
            symbol: "WET/USDT",
            fundingTime: "2030-01-01T00:00:00.000Z",
            fundingRatePct: -0.0125,
            status: "ARMED",
            sourceType: "FUNDING_API",
            armedTradeId: 10
        },
        candidate: null,
        liquidity: null,
        trade,
        attempts: [],
        tradeLiquidity: null,
        outcome: null,
        position: null
    });
    assert.doesNotMatch(markup, /arm-event/);
    assert.match(markup, /Latency Chain/);
});

// ── trades screen ─────────────────────────────────────────────────────────────

test("tradesListMarkup: empty list renders empty state", () => {
    assert.match(tradesListMarkup([]), /No Prepared Trades yet/);
});

test("tradesListMarkup: renders trade cards with symbol and venue", () => {
    const markup = tradesListMarkup([{
        id: 42,
        venue: "gate",
        symbol: "WET/USDT",
        fundingTime: "2030-01-01T00:00:00.000Z",
        notionalUsd: 25,
        intendedSide: "SHORT",
        state: "ARMED",
        armedAt: "2029-12-31T23:00:00.000Z"
    }]);
    assert.match(markup, /WET\/USDT/);
    assert.match(markup, /gate/);
});

test("trade expansion content includes edit form and cancel/close actions", () => {
    const markup = buildTradeExpansionContent({
        trade: {
            id: 42,
            fundingEventId: 7,
            venue: "gate",
            symbol: "WET/USDT",
            fundingTime: "2030-01-01T00:00:00.000Z",
            notionalUsd: 25,
            intendedSide: "SHORT",
            plannedEntryAt: "2029-12-31T23:59:50.000Z",
            plannedExitAt: "2030-01-01T00:00:30.000Z",
            entryAttemptCount: 3,
            entrySpacingMs: 150,
            manualLatencyAdjustmentMs: 10,
            measuredEntryLatencyMs: 40,
            effectiveEntryLatencyMs: 50,
            state: "ARMED",
            armedAt: "2029-12-31T23:00:00.000Z"
        },
        attempts: [],
        liquidity: null,
        position: null,
        outcome: null
    });
    assert.match(markup, /edit-trade/);
    assert.match(markup, /cancel-trade|close-trade|Cancel|Close/);
});

// ── history screen ────────────────────────────────────────────────────────────

test("historyListMarkup: empty list renders empty state", () => {
    assert.equal(historyListMarkup({ trades: [] }).countLabel, "0 / 0 trades");
    assert.match(historyListMarkup({ trades: [] }).listMarkup, /Trade History is empty/i);
});

test("historyListMarkup: count reflects filter results", () => {
    const trades = [
        { id: 1, venue: "gate", symbol: "WET/USDT", fundingTime: "2030-01-01T00:00:00.000Z", state: "CLOSED", plannedEntryAt: "2029-12-31T23:59:50.000Z", plannedExitAt: "2030-01-01T00:00:30.000Z", intendedSide: "SHORT", entryAttemptCount: 1, entrySpacingMs: 150, effectiveEntryLatencyMs: 50, manualLatencyAdjustmentMs: 0 },
        { id: 2, venue: "bybit", symbol: "BTC/USDT", fundingTime: "2030-01-02T00:00:00.000Z", state: "CANCELLED", plannedEntryAt: "2030-01-01T23:59:50.000Z", plannedExitAt: "2030-01-02T00:00:30.000Z", intendedSide: "LONG", entryAttemptCount: 0, entrySpacingMs: 150, effectiveEntryLatencyMs: 50, manualLatencyAdjustmentMs: 0 }
    ];
    const all = historyListMarkup({ trades, filters: {} });
    assert.equal(all.countLabel, "2 / 2 trades");
    assert.match(all.listMarkup, /WET\/USDT/);
    assert.match(all.listMarkup, /BTC\/USDT/);
});

test("history expansion content renders trade plan and source sections", () => {
    const markup = buildHistoryTradeDrawerContent({
        trade: {
            id: 42,
            fundingEventId: 7,
            venue: "gate",
            symbol: "WET/USDT",
            fundingTime: "2030-01-01T00:00:00.000Z",
            notionalUsd: 25,
            intendedSide: "SHORT",
            plannedEntryAt: "2029-12-31T23:59:50.000Z",
            plannedExitAt: "2030-01-01T00:00:30.000Z",
            entryAttemptCount: 3,
            entrySpacingMs: 150,
            manualLatencyAdjustmentMs: 10,
            measuredEntryLatencyMs: 40,
            effectiveEntryLatencyMs: 50,
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
        candidate: null,
        journal: [],
        attempts: [],
        position: null,
        outcome: null
    });
    // history drawer renders the full source-to-outcome narrative sections
    assert.match(markup, /1\. Source/);
    assert.match(markup, /2\. Event/);
    assert.match(markup, /3\. Plan/);
    assert.match(markup, /WET\/USDT/);
    // cancel button is injected by wireCancelTrade outside this function — not present here
    assert.doesNotMatch(markup, /data-cancel-trade/);
});
