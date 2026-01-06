package com.crypto.funding.spread;

import com.crypto.funding.market.MarketCache;
import com.crypto.funding.market.model.MarketSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpreadCalculatorTest
{
    @Test
    void findsBestDirectionOverThreshold() {
        MarketCache cache = mock(MarketCache.class);
        when(cache.getSnapshot("BTC/USDT")).thenReturn( Optional.of(new MarketSnapshot(
            "BTC/USDT",
            Map.of("binance", 100.0, "bybit", 101.0),
            Map.of("binance", 102.0, "bybit", 103.0),
            Map.of("binance", 0.0001),
            Instant.now())));

        var calc = new SpreadCalculator(cache, 1.0);
        var best = calc.bestDirection("BTC/USDT");
        assertThat(best).isPresent();
        assertThat(best.get().get("buyOn")).isEqualTo("binance");
        assertThat((Double) best.get().get("spreadPct")).isGreaterThan(1.0);
    }
}
