package com.crypto.funding.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Хранит измеренную сетевую задержку при выполнении тестовых ордеров.
 * Используется планировщиком, чтобы исполнять реальные ордера чуть раньше.
 */
@Service
public class NetworkLatencyService {

    private static final Logger log = LoggerFactory.getLogger( NetworkLatencyService.class );
    private final ConcurrentHashMap<String, Duration> perExchange = new ConcurrentHashMap<>();

    public Duration currentDelay() {
        // backward-compat: максимальное из всех, если надо общее значение
        return perExchange.values().stream().max(Duration::compareTo).orElse(Duration.ZERO);
    }

    public Duration estimate(String exchange) {
        if (exchange == null) return Duration.ZERO;
        return perExchange.getOrDefault(exchange.toLowerCase(), Duration.ZERO);
    }

    public Duration estimate(Set<String> exchanges) {
        if (exchanges == null || exchanges.isEmpty()) return Duration.ZERO;
        return exchanges.stream()
            .map(this::estimate)
            .max(Duration::compareTo)
            .orElse(Duration.ZERO);
    }

    public void record(String exchange, Duration delay) {
        if (exchange == null || delay == null) return;

        log.info( "[latency calculator] calculated estimated latency {} ", delay.get( ChronoUnit.NANOS ));

        perExchange.put(exchange.toLowerCase(), delay);
    }

    public <T> T measureAndRecord(String exchange, ThrowingSupplier<T> supplier) throws Exception {
        Objects.requireNonNull(supplier, "supplier");
        long start = System.nanoTime();
        try {
            return supplier.get();
        } finally {
            long nanos = Math.max(0, System.nanoTime() - start);

            record(exchange, Duration.ofNanos(nanos));
        }
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
