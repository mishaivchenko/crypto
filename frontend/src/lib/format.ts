export const tz = "Europe/Kyiv";

export function fmtPrice(n: number): string {
    // Prices with 2–4 decimals depending on scale
    if (n >= 1000) return n.toFixed(2);
    if (n >= 100) return n.toFixed(3);
    return n.toFixed(4);
}

export function fmtSpread(n: number): string {
    // n is already percent (e.g., 0.123)
    return `${n.toFixed(3)}%`;
}

export function fmtFundingRate(n?: number): string {
    if (n == null) return "—";
    return `${n.toFixed(3)}%`;
}

export function fmtETA(iso?: string): string {
    if (!iso) return "—";
    try {
        const dt = new Date(iso);
        return new Intl.DateTimeFormat("uk-UA", {
            hour: "2-digit",
            minute: "2-digit",
            second: "2-digit",
            timeZone: tz
        }).format(dt);
    } catch {
        return "—";
    }
}

export function minsFromSeconds(sec?: number | null): number | null {
    if (sec == null) return null;
    return Math.max(0, Math.floor(sec / 60));
}

export function fromNowMinutes(sec?: number | null): string {
    const m = minsFromSeconds(sec);
    if (m == null) return "—";
    if (m === 0) return "менше хвилини";
    return `через ${m} хв`;
}

export function ageFromNanos(tsNanos: number): number {
    const nowMs = Date.now();
    const tMs = Math.floor(tsNanos / 1_000_000);
    return Math.max(0, Math.floor((nowMs - tMs) / 1000)); // seconds
}

export function classNames(...xs: Array<string | false | null | undefined>): string {
    return xs.filter(Boolean).join(" ");
}
