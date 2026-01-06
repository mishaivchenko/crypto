package com.crypto.funding.watchlist;

import com.crypto.funding.exchanges.ExchangeRestClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Периодически обновляет funding по тем символам,
 * которые помечены как интересные для фарма фандинга.
 */
@Service
public class FundingRefresherService {

    private final FundingWatchlistService fundingWatchlist;
    private final List<ExchangeRestClient> exchanges;

    public FundingRefresherService(
        FundingWatchlistService fundingWatchlist,
        List<ExchangeRestClient> exchanges
    ) {
        this.fundingWatchlist = fundingWatchlist;
        this.exchanges = exchanges;
    }

    //@Scheduled(fixedDelay = 60_000)
    public void refreshFunding() {
        for (String unifiedSymbol : fundingWatchlist.symbols()) {
            for (ExchangeRestClient ex : exchanges) {
                try {
                    var info = ex.fetchFunding(unifiedSymbol);
                    fundingWatchlist.updateFunding(info);
                } catch (Exception e) {
                    // логгер позже, но не падаем полностью
                }
            }
        }
    }
}
