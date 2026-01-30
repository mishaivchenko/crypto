package com.crypto.funding.spread;

import com.crypto.funding.market.MarketCache;
import com.crypto.funding.market.model.MarketSnapshot;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

/**
 * Минимальная реализация для вычисления лучшего направления арбитража.
 * Логика упрощена: покупаем на бирже с минимальной bid, продаём на бирже с максимальной ask.
 */
public class SpreadCalculator
{
    private final MarketCache cache;
    private final double minSpreadPct;

    public SpreadCalculator( MarketCache cache, double minSpreadPct )
    {
        this.cache = cache;
        this.minSpreadPct = minSpreadPct;
    }

    public Optional<Map<String, Object>> bestDirection( String symbol )
    {
        Optional<MarketSnapshot> snapshotOpt = cache.getSnapshot( symbol );
        if( snapshotOpt.isEmpty() )
        {
            return Optional.empty();
        }
        MarketSnapshot s = snapshotOpt.get();

        var minBid = s.bids().entrySet().stream().min( Comparator.comparingDouble( Map.Entry::getValue ) );
        var maxAsk = s.asks().entrySet().stream().max( Comparator.comparingDouble( Map.Entry::getValue ) );

        if( minBid.isEmpty() || maxAsk.isEmpty() )
        {
            return Optional.empty();
        }

        double buy = minBid.get().getValue();
        double sell = maxAsk.get().getValue();

        double spreadPct = (sell - buy) / buy * 100.0;
        if( spreadPct < minSpreadPct )
        {
            return Optional.empty();
        }

        return Optional.of( Map.of(
            "buyOn", minBid.get().getKey(),
            "sellOn", maxAsk.get().getKey(),
            "spreadPct", spreadPct
        ) );
    }
}
