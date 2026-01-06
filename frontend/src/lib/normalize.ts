import { FundingItemRow, FundingRowNormalized, FundingSymbolCombined } from "../types/api";

export function normalizeFunding(data: FundingItemRow[] | FundingSymbolCombined[]): FundingRowNormalized[] {
    if (data.length === 0) return [];

    // Case 1: list rows
    if ("exchange" in (data[0] as any)) {
        const rows = data as FundingItemRow[];
        const bySymbol = new Map<string, FundingRowNormalized>();

        for (const r of rows) {
            const existing = bySymbol.get(r.symbolUnified) ?? {
                symbol: r.symbolUnified,
                byExchange: {},
                secondsToNext: null as number | null
            };
            (existing.byExchange as any)[r.exchange as "binance" | "bybit" | "gate"] = r;
            existing.secondsToNext = minSeconds(existing.secondsToNext, r.secondsToFunding);
            bySymbol.set(r.symbolUnified, existing);
        }
        return Array.from(bySymbol.values()).sort((a, b) => (a.symbol > b.symbol ? 1 : -1));
    }

    // Case 2: combined by symbol
    const list = data as FundingSymbolCombined[];
    return list
        .map((item) => {
            const be: FundingRowNormalized["byExchange"] = {};
            let sNext: number | null = null;

            for (const [ex, v] of Object.entries(item.funding)) {
                const asRow: FundingItemRow = {
                    exchange: v.exchange,
                    symbolUnified: item.symbol,
                    fundingRatePct: v.fundingRatePct,
                    nextFundingAt: v.nextFundingAt,
                    secondsToFunding: v.secondsToFunding,
                    lastUpdate: v.lastUpdate
                };
                (be as any)[ex] = asRow;
                sNext = minSeconds(sNext, v.secondsToFunding);
            }

            return {
                symbol: item.symbol,
                byExchange: be,
                secondsToNext: sNext
            } as FundingRowNormalized;
        })
        .sort((a, b) => (a.symbol > b.symbol ? 1 : -1));
}

function minSeconds(a: number | null, b: number): number {
    if (a == null) return b;
    return Math.min(a, b);
}
