package com.crypto.funding.subscriptions;

import com.crypto.funding.exchanges.AbstractWsClient;
import com.crypto.funding.exchanges.binance.BinanceFundingWsClient;
import com.crypto.funding.exchanges.bybit.BybitFundingWsClient;
import com.crypto.funding.exchanges.gate.GateFundingWsClient;
import com.crypto.funding.utills.SymbolMapper;
import com.crypto.funding.watchlist.FundingWatchlistService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

//@Service
public class FundingSubscriptionManager
{
    private final FundingWatchlistService fundingWatchlistService;
    private final Map<String, AbstractWsClient> clients = new ConcurrentHashMap<>();
    private static final long MAX_AGE_MS = 30_000;

    public FundingSubscriptionManager( FundingWatchlistService fundingWatchlistService, BinanceFundingWsClient binanceWsClient, BybitFundingWsClient bybitWsClient, GateFundingWsClient gateWsClient )
    {
        this.fundingWatchlistService = fundingWatchlistService;

        clients.put( binanceWsClient.name(), binanceWsClient );
        clients.put( bybitWsClient.name(), bybitWsClient );
        clients.put( gateWsClient.name(), gateWsClient );
        // старт с текущими символами
        var syms = List.copyOf( symbols() );
        clients.values().forEach( c -> c.start( syms ) );
    }

    private Set<String> symbols()
    {
        Set<String> out = new LinkedHashSet<>();
        for( var it : fundingWatchlistService.all() )
        {
            out.add( SymbolMapper.toUnified( it.symbol() ) );
        }
        return out;
    }

    @Scheduled(fixedDelay = 60_000)
    public void reconcile() {
        Set<String> syms = fundingWatchlistService.symbols();
        if (syms.isEmpty()) {
            return;
        }
        clients.values().forEach( client -> client.trySubscribe( new ArrayList<>(syms) ) );
    }

    private Set<String> exchanges()
    {
        return clients.values().stream().map( AbstractWsClient::name ).collect( Collectors.toSet() );
    }
}
