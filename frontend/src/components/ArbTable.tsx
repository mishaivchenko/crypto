import { Button } from "./ui/button";
import { Card, CardContent } from "./ui/card";
import { Skeleton } from "./ui/skeleton";
import { TBody, TD, TH, THead, TR, Table } from "./ui/table";
import { ageFromNanos, fmtPrice, fmtSpread } from "@/lib/format";
import { getArbitrageList } from "@/lib/api";
import {ArbitrageAnalysis, ExchangeQuote} from "@/types/api";
import { useCallback, useMemo, useState } from "react";
import { usePolling } from "@/hooks/usePolling";
import { toast } from "sonner";
import {Badge, Badge as SymBadge} from "./ui/badge";
import { useStableList } from "@/hooks/useStableList";

// порядок бирж в колонках (как на твоём скрине — сначала популярные)
const EX_ORDER = ["bybit", "binance", "okx", "bitget", "mexc", "gate", "htx"] as const;
const STALE_SEC = 3;

type Props = { auto: boolean; intervalMs: number };

export function ArbTable({ auto, intervalMs }: Props) {
    const [rows, setRows] = useState<ArbitrageAnalysis[] | null>(null);
    const [loading, setLoading] = useState(true);
    const [filter, setFilter] = useState("");

    const load = useCallback(async () => {
        try {
            setLoading(true);
            const list = await getArbitrageList();
            setRows(list);
        } catch (e: any) {
            toast.error("Failed to load arbitrage. Showing demo.", { description: e?.message ?? "" });
        } finally {
            setLoading(false);
        }
    }, []);
    const { lastRunAt, runNow } = usePolling(load, auto, intervalMs);

    const keyOf = useCallback((r: ArbitrageAnalysis) => r.symbol, []);
    const stable = useStableList(rows, keyOf);

    const filteredKeys = useMemo(() => {
        const f = filter.trim().toLowerCase();
        if (!f) return stable.order;
        return stable.order.filter((k) => k.toLowerCase().includes(f));
    }, [filter, stable.order]);

    // какие биржи вообще встречаются — для заголовков
    const headerExchanges = useMemo(() => {
        const set = new Set<string>();
        (rows ?? []).forEach((r) => Object.values(r.quotes).forEach((q) => set.add(q.exchange)));
        const rest = Array.from(set).filter((x) => !(EX_ORDER as readonly string[]).includes(x));
        return [...EX_ORDER.filter((x) => set.has(x)), ...rest];
    }, [rows]);

    return (
        <div className="space-y-3">
            <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
                <input
                    placeholder="Filter by symbol…"
                    className="h-9 w-full sm:w-64 rounded-xl border border-input bg-white px-3 text-sm outline-none"
                    value={filter}
                    onChange={(e) => setFilter(e.target.value)}
                />
                <div className="flex gap-2">
                    <Button variant="outline" onClick={runNow}>Refresh now</Button>
                </div>
                <div className="sm:ml-auto text-sm text-muted-foreground">Rows: {filteredKeys.length}</div>
            </div>

            <Card>
                <CardContent className="p-0">
                    {loading && (!rows || rows.length === 0) ? (
                        <div className="p-4 space-y-2">
                            {Array.from({ length: 8 }).map((_, i) => <Skeleton key={i} className="h-8 w-full" />)}
                        </div>
                    ) : filteredKeys.length === 0 ? (
                        <div className="p-6 text-center text-muted-foreground">No data</div>
                    ) : (
                        <Table className="[font-variant-numeric:tabular-nums]">
                            <THead>
                                <TR>
                                    <TH className="w-36">Монета</TH>
                                    {/* пара/связка ты выводишь сам на бэке — у нас её нет; оставляем место */}
                                    <TH className="w-48">Зв’язка</TH>
                                    {headerExchanges.map((ex) => (
                                        <TH key={ex} className="text-center w-40">{ex}</TH>
                                    ))}
                                    <TH className="w-28 text-center">Spread %</TH>
                                </TR>
                            </THead>
                            <TBody>
                                {filteredKeys.map((key) => {
                                    const r = stable.map.get(key);
                                    // если запись пропала из ответа — оставляем "нет данных"
                                    if (!r) {
                                        return (
                                            <TR key={key} className="opacity-70">
                                                <TD><Badge className="mr-2">{key}</Badge>{key}</TD>
                                                <TD className="text-muted-foreground">—</TD>
                                                {headerExchanges.map((ex) => (
                                                    <TD key={ex} className="text-center"><NoDataDot /></TD>
                                                ))}
                                                <TD className="text-center">—</TD>
                                            </TR>
                                        );
                                    }

                                    return (
                                        <TR key={r.symbol}>
                                            <TD className="whitespace-nowrap"><Badge className="mr-2">{r.symbol}</Badge>{r.symbol}</TD>
                                            {/* сюда можно вывести твою «зв’язка» (best buy/sell), пока просто показываем 2 лучших места */}
                                            <TD className="text-sm">
                                                <div className="flex items-center gap-3">
                                                    <div className="inline-flex items-center gap-1">
                                                        <Badge variant="muted">{r.bestBuyExchange ?? "—"}</Badge>
                                                        <span className="font-medium">{r.bestBuyPrice ? fmtPrice(r.bestBuyPrice) : "—"}</span>
                                                    </div>
                                                    <div className="inline-flex items-center gap-1">
                                                        <Badge variant="muted">{r.bestSellExchange ?? "—"}</Badge>
                                                        <span className="font-medium">{r.bestSellPrice ? fmtPrice(r.bestSellPrice) : "—"}</span>
                                                    </div>
                                                </div>
                                            </TD>

                                            {headerExchanges.map((ex) => (
                                                <TD key={ex} className="text-center">
                                                    <QuoteCell quote={(r.quotes as any)[ex]} />
                                                </TD>
                                            ))}

                                            <TD className="text-center">
                                                <Badge variant={r.spreadPct > 0 ? "success" : "muted"}>{fmtSpread(r.spreadPct)}</Badge>
                                            </TD>
                                        </TR>
                                    );
                                })}
                            </TBody>
                        </Table>
                    )}
                </CardContent>
            </Card>

            <div className="text-xs text-muted-foreground">
                Last auto refresh: {lastRunAt ? new Date(lastRunAt).toLocaleTimeString("uk-UA") : "—"}
            </div>
        </div>
    );
}

function QuoteCell({ quote }: { quote?: ExchangeQuote }) {
    if (!quote) return <NoDataDot />;
    const age = ageFromNanos(quote.tsNanos);
    const fresh = age <= STALE_SEC;
    return (
        <div className="inline-flex flex-col items-center gap-0.5">
            <span className={`inline-block h-2 w-2 rounded-full ${fresh ? "bg-green-500" : "bg-yellow-500"}`} title={`${age}s`} />
            <div className="text-xs">Bid <span className="font-semibold">{fmtPrice(quote.bid)}</span></div>
            <div className="text-xs">Ask <span className="font-semibold">{fmtPrice(quote.ask)}</span></div>
        </div>
    );
}

function NoDataDot() {
    return (
        <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
      <span className="inline-block h-2 w-2 rounded-full bg-red-500" /> —
    </span>
    );
}
