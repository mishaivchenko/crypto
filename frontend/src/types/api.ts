// === API DTOs (as you described) ===

export type ExchangeQuote = {
    exchange: string;
    bid: number;
    ask: number;
    tsNanos: number;
};

export type ArbitrageAnalysis = {
    symbol: string;                       // "BTC/USDT"
    expireAt: string;                     // ISO
    quotes: Record<string, ExchangeQuote>;
    bestBuyExchange: string | null;
    bestBuyPrice: number;
    bestSellExchange: string | null;
    bestSellPrice: number;
    spreadPct: number;                    // e.g. 0.123 means 0.123%
};

// Funding (two shapes supported: list-of-rows OR combined-by-symbol)
export type FundingItemRow = {
    exchange: string;          // "binance", "bybit", "gate"
    symbolUnified: string;     // "BTC/USDT"
    fundingRatePct: number;    // 0.0123 -> already *100 means "0.0123%"
    nextFundingAt: string;     // ISO
    secondsToFunding: number;
    lastUpdate: string;
};

// When API returns one object per symbol with a "funding" map
export type FundingSymbolCombined = {
    symbol: string;
    expireAt: string;
    funding: Record<
        string,
        {
            exchange: string;
            fundingRatePct: number;
            nextFundingAt: string;
            secondsToFunding: number;
            lastUpdate: string;
        }
    >;
};

// Normalized for UI table
export type FundingRowNormalized = {
    symbol: string;
    byExchange: {
        binance?: FundingItemRow;
        bybit?: FundingItemRow;
        gate?: FundingItemRow;
    };
    // Seconds to next funding among available exchanges (min)
    secondsToNext: number | null;
};
