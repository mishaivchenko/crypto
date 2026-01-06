package com.crypto.funding.watchlist;

import com.crypto.funding.utills.SymbolMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Хранит пары, которые пришли как арбитражные сигналы (типа "купи тут, продай там").
 * Ничего не знает про funding.
 */
@Service
public class ArbitrageWatchlistService {

    public static record Item(
        String symbol,        // unified ("BTC/USDT")
        Instant expireAt      // TTL
    ) {}

    private final ConcurrentHashMap<String, Item> items = new ConcurrentHashMap<>();

    public void addSymbol(String rawSymbol) {
        String unified = SymbolMapper.toUnified(rawSymbol);

        items.compute(unified, (sym, existing) -> {
            Instant ttl = Instant.now().plus(1, ChronoUnit.DAYS);
            if (existing == null) {
                return new Item(sym, ttl);
            } else {
                return new Item(sym, ttl); // просто продлеваем жизнь
            }
        });
    }

    public Set<Item> all() {
        return Set.copyOf(items.values());
    }

    @Scheduled(fixedDelay = 10_000)
    void evictExpired() {
        Instant now = Instant.now();
        items.values().removeIf(i -> i.expireAt().isBefore(now));
    }
}
