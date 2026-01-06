import { Tabs, TabsContent, TabsList, TabsTrigger } from "./components/ui/tabs";
import { Header } from "./components/Header";
import { ArbTable } from "./components/ArbTable";
import { FundingTable } from "./components/FundingTable";
import { useEffect, useMemo, useState } from "react";
import { Toaster } from "sonner";
import { ExportCsv } from "./components/ExportCsv";
import { getArbitrageList } from "./lib/api";

type Settings = {
    auto: boolean;
    intervalMs: number;
    tab: "arb" | "funding";
};

const LS_KEY = "arb-ui-settings";

export default function App() {
    const [settings, setSettings] = useState<Settings>(() => {
        const saved = localStorage.getItem(LS_KEY);
        return saved ? JSON.parse(saved) : { auto: true, intervalMs: 1000, tab: "arb" };
    });

    useEffect(() => {
        localStorage.setItem(LS_KEY, JSON.stringify(settings));
    }, [settings]);

    // CSV export (Arb current snapshot)
    const [arbSnapshot, setArbSnapshot] = useState<any[]>([]);
    const refreshExport = async () => {
        try {
            const data = await getArbitrageList();
            setArbSnapshot(
                data.map((r) => ({
                    symbol: r.symbol,
                    bestBuyExchange: r.bestBuyExchange ?? "",
                    bestBuyPrice: r.bestBuyPrice,
                    bestSellExchange: r.bestSellExchange ?? "",
                    bestSellPrice: r.bestSellPrice,
                    spreadPct: r.spreadPct
                }))
            );
        } catch {
            // ignore
        }
    };

    const headerTitle = useMemo(
        () => (settings.tab === "arb" ? "Arbitrage Monitor" : "Funding Monitor"),
        [settings.tab]
    );

    return (
        <div className="container py-4 space-y-4">
            <Header
                title={headerTitle}
                autoEnabled={settings.auto}
                onToggleAuto={(v) => setSettings((s) => ({ ...s, auto: v }))}
                intervalMs={settings.intervalMs}
                onIntervalChange={(ms) => setSettings((s) => ({ ...s, intervalMs: ms }))}
                onRefreshNow={undefined}
                lastUpdatedAt={null}
            />

            <Tabs value={settings.tab} onValueChange={(v) => setSettings((s) => ({ ...s, tab: v as Settings["tab"] }))}>
                <TabsList>
                    <TabsTrigger value="arb">Arbitrage</TabsTrigger>
                    <TabsTrigger value="funding">Funding</TabsTrigger>
                </TabsList>

                <div className="flex items-center gap-2 py-2">
                    {settings.tab === "arb" && (
                        <ExportCsv filename="arbitrage.csv" rows={arbSnapshot} />
                    )}
                    {settings.tab === "arb" && (
                        <button
                            className="text-sm text-muted-foreground underline"
                            onClick={refreshExport}
                            title="Take fresh snapshot for CSV"
                        >
                            refresh snapshot
                        </button>
                    )}
                </div>

                <TabsContent value="arb">
                    <ArbTable auto={settings.auto} intervalMs={settings.intervalMs} />
                </TabsContent>
                <TabsContent value="funding">
                    <FundingTable auto={settings.auto} intervalMs={settings.intervalMs} />
                </TabsContent>
            </Tabs>

            <Toaster richColors position="bottom-right" />
        </div>
    );
}
