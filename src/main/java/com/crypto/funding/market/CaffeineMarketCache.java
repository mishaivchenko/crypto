package com.crypto.funding.market;

import com.crypto.funding.market.model.Quote;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CaffeineMarketCache implements MarketCache
{

    // ключ = exchange + "|" + unified ("binance|SD/USDT")
    private final Cache<String, Quote> cache = Caffeine.newBuilder()
                                                       .expireAfterWrite( Duration.ofMinutes( 5 ) ) // «устаревает»
                                                       .maximumSize( 100_000 )
                                                       .build();

    private static String k( String ex, String sym )
    {
        return ex + "|" + sym;
    }

    @Override
    public void put( String exchange, String unifiedSymbol, Quote q )
    {
        cache.put( k( exchange, unifiedSymbol ), q );
    }

    @Override
    public Quote get( String exchange, String unifiedSymbol )
    {
        return cache.getIfPresent( k( exchange, unifiedSymbol ) );
    }

    @Override
    public Map<String, Quote> getAll( String unifiedSymbol )
    {
        Map<String, Quote> out = new LinkedHashMap<>();
        // Быстро: пройдёмся по возможным биржам (перечень — в коде проекта)
        for( String ex : List.of( "binance", "bybit", "gate" ) )
        {
            Quote q = get( ex, unifiedSymbol );
            if( q != null )
            {
                out.put( ex, q );
            }
        }
        return out;
    }

    public boolean isFresh(String exchange, String unifiedSymbol, long maxAgeMillis) {
        Quote q = get(exchange, unifiedSymbol);
        if (q == null) return false;
        long ageMillis = (System.nanoTime() - q.tsNanos()) / 1_000_000L;
        return ageMillis >= 0 && ageMillis <= maxAgeMillis;
    }
}
