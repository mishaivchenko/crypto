import { useCallback, useEffect, useRef, useState } from "react";

export function usePolling(
    fn: () => Promise<void> | void,
    enabled: boolean,
    intervalMs: number
) {
    const timerRef = useRef<number | null>(null);
    const [lastRunAt, setLastRunAt] = useState<number | null>(null);

    const clear = useCallback(() => {
        if (timerRef.current != null) {
            window.clearInterval(timerRef.current);
            timerRef.current = null;
        }
    }, []);

    const tick = useCallback(async () => {
        await fn();
        setLastRunAt(Date.now());
    }, [fn]);

    useEffect(() => {
        clear();
        if (enabled && intervalMs > 0) {
            // Run immediately, then set interval
            tick();
            timerRef.current = window.setInterval(tick, intervalMs) as unknown as number;
        }
        return clear;
    }, [enabled, intervalMs, clear, tick]);

    const runNow = useCallback(async () => {
        await tick();
    }, [tick]);

    return { lastRunAt, runNow };
}
