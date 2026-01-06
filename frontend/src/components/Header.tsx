import { Button } from "./ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "./ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "./ui/select";
import { Switch } from "./ui/switch";
import { tz } from "@/lib/format";
import { useEffect, useMemo, useState } from "react";

type Props = {
    title: string;
    autoEnabled: boolean;
    onToggleAuto: (v: boolean) => void;
    intervalMs: number;
    onIntervalChange: (ms: number) => void;
    onRefreshNow?: () => void;
    lastUpdatedAt?: number | null;
};

const INTERVALS = [1000, 5000, 10000, 30000, 60000] as const;

export function Header({
                           title,
                           autoEnabled,
                           onToggleAuto,
                           intervalMs,
                           onIntervalChange,
                           onRefreshNow,
                           lastUpdatedAt
                       }: Props) {
    const [now, setNow] = useState<Date>(new Date());
    useEffect(() => {
        const t = setInterval(() => setNow(new Date()), 1000);
        return () => clearInterval(t);
    }, []);

    const lastUpdatedText = useMemo(() => {
        if (!lastUpdatedAt) return "—";
        const d = new Date(lastUpdatedAt);
        return new Intl.DateTimeFormat("uk-UA", {
            hour: "2-digit",
            minute: "2-digit",
            second: "2-digit",
            timeZone: tz
        }).format(d);
    }, [lastUpdatedAt]);

    return (
        <Card className="mb-3 md:mb-4">
            <CardHeader className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
                <CardTitle className="text-lg sm:text-xl">{title}</CardTitle>
                <div className="text-xs sm:text-sm text-muted-foreground">
                    {new Intl.DateTimeFormat("uk-UA", { timeStyle: "medium", dateStyle: "short", timeZone: tz }).format(now)}
                </div>
            </CardHeader>

            <CardContent className="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-center">
                <div className="flex items-center justify-between sm:justify-start gap-2">
                    <span className="text-sm text-muted-foreground">Auto-refresh</span>
                    <Switch checked={autoEnabled} onCheckedChange={onToggleAuto} aria-label="Auto refresh toggle" />
                </div>

                <div className="flex items-center gap-2 sm:w-auto">
                    <span className="text-sm text-muted-foreground">Interval</span>
                    <Select value={String(intervalMs)} onValueChange={(v) => onIntervalChange(Number(v))}>
                        <SelectTrigger className="w-full sm:w-28 h-9 rounded-xl">
                            <SelectValue placeholder="Interval" />
                        </SelectTrigger>
                        <SelectContent>
                            {INTERVALS.map((ms) => (
                                <SelectItem key={ms} value={String(ms)}>{ms / 1000}s</SelectItem>
                            ))}
                        </SelectContent>
                    </Select>
                </div>

                {onRefreshNow && (
                    <div className="sm:ml-2">
                        <Button variant="outline" onClick={onRefreshNow}>Refresh now</Button>
                    </div>
                )}

                <div className="sm:ml-auto text-xs sm:text-sm text-muted-foreground">
                    Last update: <span className="font-medium text-foreground">{lastUpdatedText}</span>
                </div>
            </CardContent>
        </Card>
    );
}
