package com.crypto.funding.subscriptions;

import com.crypto.funding.exchanges.AbstractWsClient;
import com.crypto.funding.exchanges.binance.BinanceArbitrageWsClient;
import com.crypto.funding.exchanges.bybit.BybitArbitrageWsClient;
import com.crypto.funding.exchanges.gate.GateArbitrageWsClient;
import com.crypto.funding.market.MarketCache;
import com.crypto.funding.utills.SymbolMapper;
import com.crypto.funding.watchlist.ArbitrageWatchlistService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class SubscriptionManager
{

    private final ArbitrageWatchlistService arbitrageWatchlistService;
    private final Map<String, AbstractWsClient> clients = new ConcurrentHashMap<>();
    private final MarketCache marketCache;
    private static final long MAX_AGE_MS = 30_000;

    // стартуем клиентов единоразово (подпишутся в onOpen)
    public SubscriptionManager( ArbitrageWatchlistService arbitrageWatchlistService, MarketCache cache, BinanceArbitrageWsClient binanceArbitrageWsClient, GateArbitrageWsClient gateArbitrageWsClient,
        BybitArbitrageWsClient bybitArbitrageWsClient )
    {
        this.arbitrageWatchlistService = arbitrageWatchlistService;
        this.marketCache = cache;

        clients.put( binanceArbitrageWsClient.name(), binanceArbitrageWsClient );
        clients.put( bybitArbitrageWsClient.name(), bybitArbitrageWsClient );
        clients.put( gateArbitrageWsClient.name(), gateArbitrageWsClient );
        // старт с текущими символами
        var syms = List.copyOf( symbols() );
        //clients.values().forEach( c -> c.start( syms ) );
    }

    // периодический ресаб (если телега добавила/удалила монеты)
    @Scheduled( fixedDelay = 60_000 )
    public void reconcile()
    {

        Set<String> desired = symbols();
        if( desired.isEmpty() )
        {
            return;
        }

        Map<String, List<String>> expiredMap = new HashMap<>();
        for( String sym : desired )
        {
            for( String ex : exchanges() )
            {
                if( !marketCache.isFresh( ex, sym, MAX_AGE_MS ) )
                {
                    if( !expiredMap.containsKey( ex ) )
                    {
                        expiredMap.put( ex, new ArrayList<>() );
                    }
                    expiredMap.get( ex ).add( sym );
                }
            }
        }

        // 2) прогоняем по биржам и символам, адресно добиваем «немые»
        for( String sym : desired )
        {

            // BINANCE
            if( !marketCache.isFresh( "binance", sym, MAX_AGE_MS ) )
            {
                clients.get( "binance" ).trySubscribe( expiredMap.get( "binance" ) );
            }
            // BYBIT
            if( !marketCache.isFresh( "bybit", sym, MAX_AGE_MS ) )
            {
                clients.get( "bybit" ).trySubscribe( expiredMap.get( "bybit" ) );
            }
            // GATE
            if( !marketCache.isFresh( "gate", sym, MAX_AGE_MS ) )
            {
                clients.get( "gate" ).trySubscribe( expiredMap.get( "gate" ) );
            }
        }
    }

    private Set<String> symbols()
    {
        Set<String> out = new LinkedHashSet<>();
        for( var it : arbitrageWatchlistService.all() )
        {
            out.add( SymbolMapper.toUnified( it.symbol() ) );
        }
        return out;
    }

    private Set<String> exchanges()
    {
        return clients.values().stream().map( AbstractWsClient::name ).collect( Collectors.toSet() );
    }
}
