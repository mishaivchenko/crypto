package com.crypto.funding.watchlist;

import com.crypto.funding.utills.SymbolMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Хранит пары, по которым мы хотим фармить funding.
 * Здесь же накапливаются fundingRate/nextFundingTime по биржам.
 */
@Service
public class FundingWatchlistService
{

    // Что мы храним по бирже
    public record WatchFunding(
        String exchange,          // "binance", "bybit", "gate"
        double fundingRatePct,    // уже умножено на 100
        Instant nextFundingAt,
        long secondsToFunding,
        Instant lastUpdate
    )
    {
    }

    // Вся инфа по конкретному символу
    public record Item(
        String symbol,                       // unified ("BTC/USDT")
        Instant expireAt,
        Map<String, WatchFunding> funding

        // exchange -> data
    )
    {
        @Override
        public String toString()
        {

            return "{ symbol='" + symbol + '\'' +
                   ", expireAt=" + expireAt +
                   ", funding=" + funding.entrySet().stream().findAny() +
                   '}';
        }
    }

    private final Map<String, Item> items = new ConcurrentHashMap<>();

    /**
     * Добавить символ (сигнал на фарм фандинга).
     * Обновляем/создаём слот под него с новым TTL, но funding не трогаем.
     */
    public void addSymbol( String rawSymbol )
    {
        String unified = SymbolMapper.toUnified( rawSymbol );

        items.compute( unified, ( sym, existing ) -> {
            Instant ttl = Instant.now().plus( 1, ChronoUnit.DAYS );
            if( existing == null )
            {
                return new Item( sym, ttl, new ConcurrentHashMap<>() );
            }
            else
            {
                return new Item( sym, ttl, existing.funding() );
            }
        } );
    }

    public void updateFunding( FundingInfo info )
    {
        String unified = info.symbolUnified();

        items.compute( unified, ( sym, existing ) -> {
            Instant ttl = Instant.now().plus( 1, ChronoUnit.DAYS );

            Map<String, WatchFunding> fundingMap =
                ( existing == null )
                ? new ConcurrentHashMap<>()
                : new ConcurrentHashMap<>( existing.funding() );

            // берём то, что пришло с биржи
            Instant next = info.nextFundingAt();
            long secLeft = info.secondsToFunding();

            boolean missingNext =
                next == null || next.toEpochMilli() <= 0L;

            if( missingNext && existing != null )
            {
                // пробуем взять nextFundingAt у других бирж по этому символу

                Instant reusedNext = null;

                // 1) приоритет — binance, если есть
                WatchFunding binanceFunding = existing.funding().get( "binance" );
                if( binanceFunding != null
                    && binanceFunding.nextFundingAt() != null
                    && binanceFunding.nextFundingAt().toEpochMilli() > 0L )
                {
                    reusedNext = binanceFunding.nextFundingAt();
                }

                // 2) иначе — любая другая биржа с валидным nextFundingAt
                if( reusedNext == null )
                {
                    for( WatchFunding wf : existing.funding().values() )
                    {
                        if( wf.nextFundingAt() != null
                            && wf.nextFundingAt().toEpochMilli() > 0L )
                        {
                            reusedNext = wf.nextFundingAt();
                            break;
                        }
                    }
                }

                if( reusedNext != null )
                {
                    next = reusedNext;
                    secLeft = Math.max( 0L, Duration.between( Instant.now(), next ).getSeconds() );
                }
            }

            fundingMap.put(
                info.exchange(),
                new WatchFunding(
                    info.exchange(),
                    info.fundingRatePct(),
                    next,
                    secLeft,
                    Instant.now()
                )
            );

            return new Item( sym, ttl, fundingMap );
        } );
    }

    /**
     * Вернуть всё наружу (REST)
     */
    public Collection<Item> all()
    {
        return items.values().stream().toList();
    }

    /**
     * Служебно, для планировщика - какие символы надо рефрешить
     */
    public Set<String> symbols()
    {
        return items.keySet();
    }

    @Scheduled( fixedDelay = 60_000 )
    void evictExpired()
    {
        Instant now = Instant.now();
        items.values().removeIf( i -> i.expireAt().isBefore( now ) );
    }
}
