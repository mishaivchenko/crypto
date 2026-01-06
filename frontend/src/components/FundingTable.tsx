import { Button } from "./ui/button";
import { Badge } from "./ui/badge";
import { Card, CardContent } from "./ui/card";
import { Skeleton } from "./ui/skeleton";
import { Table, THead, TR, TH, TBody, TD } from "./ui/table";
import { fromNowMinutes, fmtETA, fmtFundingRate, minsFromSeconds } from "../lib/format";
import { getFunding, refreshFunding } from "../lib/api";
import { FundingRowNormalized } from "../types/api";
import { useCallback, useMemo, useState } from "react";
import { usePolling } from "../hooks/usePolling";
import { toast } from "sonner";
import { useStableList } from "@/hooks/useStableList";

const EX_ORDER = ["bybit", "binance", "okx", "bitget", "mexc", "gate", "htx"] as const;

type Props = { auto: boolean; intervalMs: number };

export function FundingTable({ auto, intervalMs }: Props) {
    const [rows, setRows] = useState<FundingRowNormalized[] | null>(null);
    const [loading, setLoading] = useState(true);
    const [filter, setFilter] = useState("");

    const load = useCallback(async () => {
        try {
            setLoading(true);
            const r = await getFunding();
            setRows(r);
        } catch (e: any) {
            toast.error("Failed to load funding. Showing demo.", { description: e?.message ?? "" });
        } finally {
            setLoading(false);
        }
    }, []);
    const { lastRunAt, runNow } = usePolling(load, auto, intervalMs);

    const stable = useStableList(rows, (r) => r.symbol);

    const filteredKeys = useMemo(() => {
        const f = filter.trim().toLowerCase();
        if (!f) return stable.order;
        return stable.order.filter((k) => k.toLowerCase().includes(f));
    }, [filter, stable.order]);

    const headerExchanges = useMemo(() => {
        const set = new Set<string>();
        (rows ?? []).forEach((r) => Object.values(r.byExchange).forEach((x: any) => x && set.add(x.exchange)));
        const rest = Array.from(set).filter((x) => !(EX_ORDER as readonly string[]).includes(x));
        return [...EX_ORDER.filter((x) => set.has(x)), ...rest];
    }, [rows]);

    const onRefreshFunding = useCallback(async () => {
        try {
            await refreshFunding();
            await load();
            toast.success("Funding refresh triggered");
        } catch (e: any) {
            toast.error("Refresh funding failed", { description: e?.message ?? "" });
        }
    }, [load]);

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
                    <Button variant="default" onClick={onRefreshFunding}>Refresh funding now</Button>
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
                                    <TH className="w-36">Symbol</TH>
                                    {headerExchanges.map((ex) => (
                                        <TH key={ex} className="text-center w-44">{ex} % / ETA</TH>
                                    ))}
                                    <TH className="w-28 text-center">Next (min)</TH>
                                    <TH className="w-24">Actions</TH>
                                </TR>
                            </THead>
                            <TBody>
                                {filteredKeys.map((key) => {
                                    const r = stable.map.get(key);
                                    if (!r) {
                                        return (
                                            <TR key={key} className="opacity-70">
                                                <TD><Badge className="mr-2">{key}</Badge>{key}</TD>
                                                {headerExchanges.map((ex) => (
                                                    <TD key={ex} className="text-center"><NoData /></TD>
                                                ))}
                                                <TD className="text-center">—</TD>
                                                <TD>—</TD>
                                            </TR>
                                        );
                                    }

                                    const minMin = minsFromSeconds(r.secondsToNext);
                                    const alert =
                                        minMin != null && minMin < 10 ? <Badge variant="danger">менше 10 хв</Badge> :
                                            minMin != null && minMin < 30 ? <Badge variant="warning">менше 30 хв</Badge> :
                                                null;

                                    return (
                                        <TR key={r.symbol}>
                                            <TD className="whitespace-nowrap"><Badge className="mr-2">{r.symbol}</Badge>{alert}</TD>

                                            {headerExchanges.map((ex) => {
                                                const cell = (r.byExchange as any)[ex] as
                                                    | { exchange: string; fundingRatePct: number; nextFundingAt: string; secondsToFunding: number; lastUpdate: string }
                                                    | undefined;
                                                return (
                                                    <TD key={ex} className="text-center">
                                                        {cell ? <FundingCell {...cell} /> : <NoData />}
                                                    </TD>
                                                );
                                            })}

                                            <TD className="text-center">{minMin ?? "—"}</TD>
                                            <TD><Button size="sm" variant="outline" onClick={() => toast.message(`Pinned ${r.symbol}`)}>Pin</Button></TD>
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

function FundingCell({
                         fundingRatePct,
                         nextFundingAt,
                         secondsToFunding,
                         lastUpdate
                     }: {
    exchange: string;
    fundingRatePct: number;
    nextFundingAt: string;
    secondsToFunding: number;
    lastUpdate: string;
}) {
    const variant = fundingRatePct < 0 ? "danger" : fundingRatePct > 0 ? "success" : "default";
    return (
        <div className="inline-flex flex-col items-center gap-0.5">
            <Badge variant={variant as any}>{fmtFundingRate(fundingRatePct)}</Badge>
            <span className="text-xs text-muted-foreground">
        {fmtETA(nextFundingAt)} • {fromNowMinutes(secondsToFunding)} • {fmtETA(lastUpdate)}
      </span>
        </div>
    );
}

function NoData() {
    return (
        <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
      <span className="inline-block h-2 w-2 rounded-full bg-red-500" /> —
    </span>
    );
}
