import { useMemo, useRef } from "react";

/**
 * Держит стабильный порядок элементов. Ключ вычисляется из записи (например, symbol).
 * - Новые ключи ДОБАВЛЯЮТСЯ в конец.
 * - Пропавшие ключи остаются (можно пометить их как stale на UI).
 */
export function useStableList<T>(
    items: T[] | null | undefined,
    keyOf: (t: T) => string
) {
    const orderRef = useRef<string[]>([]);
    const map = useMemo(() => {
        const m = new Map<string, T>();
        (items ?? []).forEach((it) => m.set(keyOf(it), it));
        const order = orderRef.current;

        // добавляем новые ключи — в конец
        for (const k of m.keys()) {
            if (!order.includes(k)) order.push(k);
        }
        // НЕ удаляем отсутствующие — пусть живут (UI может показать "нет данных")

        return { order, map: m };
    }, [items, keyOf]);

    return map; // { order: string[], map: Map<string,T> }
}
