import { ArbitrageAnalysis, FundingItemRow, FundingRowNormalized, FundingSymbolCombined } from "../types/api";
import { normalizeFunding } from "../lib/normalize";

const API_BASE = (() => {
    try {
        // обращение к `import.meta` через eval чтобы избежать ошибок компиляции TS1343/TS2339
        // eslint-disable-next-line no-eval
        const im = eval("typeof import.meta !== 'undefined' ? import.meta : undefined") as any;
        return im?.env?.VITE_API_BASE ?? "/";
    } catch {
        return "/";
    }
})();

type FetchOpts = {
    method?: "GET" | "POST";
    signal?: AbortSignal;
    cache?: RequestCache;
    headers?: Record<string, string>;
    body?: unknown;
};

async function fetchJson<T>(path: string, opts: FetchOpts = {}): Promise<T> {
    const url = path.startsWith("http") ? path : `${API_BASE.replace(/\/$/, "")}${path}`;
    const res = await fetch(url, {
        method: opts.method ?? "GET",
        headers: {
            "Content-Type": "application/json",
            ...(opts.headers ?? {})
        },
        body: opts.body ? JSON.stringify(opts.body) : undefined,
        cache: opts.cache ?? "no-store",
        signal: opts.signal
    });
    if (!res.ok) {
        const text = await res.text();
        throw new Error(`HTTP ${res.status} ${res.statusText}: ${text}`);
    }
    return res.json() as Promise<T>;
}

// --- Public API ---
export async function getArbitrageList(signal?: AbortSignal): Promise<ArbitrageAnalysis[]> {
    try {
        return await fetchJson<ArbitrageAnalysis[]>("/api/watchlist/arbitrage", { signal });
    } catch (e) {
        // Fallback demo data
        return [
            {
                symbol: "N/A",
                expireAt: "N/A",
                quotes: {
                    binance: { exchange: "binance", bid: 0.0, ask: 0.0, tsNanos: 0 },
                    bybit: { exchange: "bybit", bid: 0.0, ask: 0.0, tsNanos: 0 },
                    gate: { exchange: "gate", bid: 0.0, ask: 0.0, tsNanos: 0 }
                },
                bestBuyExchange: "N/A",
                bestBuyPrice: 0,
                bestSellExchange: "N/A",
                bestSellPrice: 0,
                spreadPct: 0.0
            }
        ];
    }
}

export async function getArbitrageRaw(signal?: AbortSignal): Promise<unknown> {
    return fetchJson<unknown>("/api/watchlist/arbitrage/raw", { signal });
}

export async function getFunding(signal?: AbortSignal): Promise<FundingRowNormalized[]> {
    try {
        const resp = await fetchJson<unknown>("/api/watchlist/funding?", { signal });

        // The endpoint may return either:
        // 1) FundingItemRow[] OR
        // 2) FundingSymbolCombined[]
        if (Array.isArray(resp) && resp.length > 0) {
            const first = resp[0] as any;
            if ("exchange" in first && "symbolUnified" in first) {
                return normalizeFunding(resp as FundingItemRow[]);
            }
            if ("symbol" in first && "funding" in first) {
                return normalizeFunding(resp as FundingSymbolCombined[]);
            }
        }
        return [];
    } catch {
        // fallback demo consistent with your example
        const demo: FundingSymbolCombined[] = [
            {
                symbol: "N/A",
                expireAt: "N/A",
                funding: {
                    binance: { exchange: "binance", fundingRatePct: 0, nextFundingAt: "N/A", secondsToFunding: 0, lastUpdate: "N/A" },
                    bybit: { exchange: "bybit", fundingRatePct: 0, nextFundingAt: "N/A", secondsToFunding: 0 , lastUpdate: "N/A" } ,
                    gate: { exchange: "gate", fundingRatePct: 0, nextFundingAt: "N/A", secondsToFunding: 0  , lastUpdate: "N/A" }
                }
            }
        ];
        return normalizeFunding(demo);
    }
}

export async function refreshFunding(): Promise<void> {
    await fetchJson<void>("/api/watchlist/funding?doRefresh=true", {
        method: "GET"
    });
}

export { API_BASE };
