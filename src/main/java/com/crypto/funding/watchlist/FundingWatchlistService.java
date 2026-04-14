package com.crypto.funding.watchlist;

import com.crypto.funding.utills.SymbolMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Candidate watchlist populated by Telegram ingest and exchange refreshers.
 * It is an observation layer, not the target trading domain model.
 */
@Service
public class FundingWatchlistService
{

    // Что мы храним по бирже
    public record WatchFunding(
        String exchange,
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
     * Add or refresh an observed funding-event candidate symbol.
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

                for( WatchFunding wf : existing.funding().values() )
                {
                    if( wf.nextFundingAt() != null
                        && wf.nextFundingAt().toEpochMilli() > 0L
                        && wf.nextFundingAt().isAfter( Instant.now() ) )
                    {
                        reusedNext = wf.nextFundingAt();
                        break;
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

    public Optional<WatchFunding> findFunding( String rawSymbol, String rawExchange )
    {
        if( rawSymbol == null || rawExchange == null )
        {
            return Optional.empty();
        }
        String symbol = SymbolMapper.toUnified( rawSymbol );
        String exchange = rawExchange.trim().toLowerCase();
        Item item = items.get( symbol );
        if( item == null )
        {
            return Optional.empty();
        }
        WatchFunding funding = item.funding().get( exchange );
        if( funding == null )
        {
            return Optional.empty();
        }
        if( funding.nextFundingAt() == null || !funding.nextFundingAt().isAfter( Instant.now() ) )
        {
            return Optional.empty();
        }
        return Optional.of( funding );
    }

    @Scheduled( fixedDelay = 60_000 )
    void evictExpired()
    {
        Instant now = Instant.now();
        items.values().removeIf( i -> i.expireAt().isBefore( now ) );
    }
}
