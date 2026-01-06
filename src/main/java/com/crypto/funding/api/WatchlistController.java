package com.crypto.funding.api;

import com.crypto.funding.market.ArbitrageViewService;
import com.crypto.funding.watchlist.ArbitrageWatchlistService;
import com.crypto.funding.watchlist.FundingRefresherService;
import com.crypto.funding.watchlist.FundingWatchlistService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@RestController
public class WatchlistController
{

    private final ArbitrageWatchlistService arbitrageWatchlist;
    private final FundingWatchlistService fundingWatchlist;
    private final FundingRefresherService fundingRefresher;
    private final ArbitrageViewService arbitrageViewService;

    public WatchlistController(
        ArbitrageWatchlistService arbitrageWatchlist,
        FundingWatchlistService fundingWatchlist, FundingRefresherService fundingRefresher, ArbitrageViewService arbitrageViewService
    )
    {
        this.arbitrageWatchlist = arbitrageWatchlist;
        this.fundingWatchlist = fundingWatchlist;
        this.fundingRefresher = fundingRefresher;
        this.arbitrageViewService = arbitrageViewService;
    }


    /** “Сырые” пары арбитража (для отладки телеги/парсера) */
    @GetMapping("/api/watchlist/arbitrage/raw")
    public Set<ArbitrageWatchlistService.Item> arbitrageRaw() {
        return arbitrageWatchlist.all();
    }

    /** Готовый арбитраж-вид: котировки по биржам + лучший buy/sell + спред */
    @GetMapping("/api/watchlist/arbitrage")
    public List<ArbitrageViewService.ArbitrageAnalysis> arbitrageView() {
        return arbitrageViewService.buildView();
    }

    // Основной endpoint для UI: просто отдать текущее состояние,
    // которое наполняют WS-клиенты (BinanceFundingWsClient и т.д.)
    @GetMapping("/api/watchlist/funding")
    public Collection<FundingWatchlistService.Item> funding() {
        return fundingWatchlist.all();
    }

    // Тестовый endpoint: руками дернуть REST к биржам и обновить состояние.
    // Можно вызывать только из Postman / отдельной кнопки.
    @GetMapping("/api/watchlist/funding/test-refresh")
    public Collection<FundingWatchlistService.Item> fundingTestRefresh() {
        fundingRefresher.refreshFunding();
        return fundingWatchlist.all();
    }
}
