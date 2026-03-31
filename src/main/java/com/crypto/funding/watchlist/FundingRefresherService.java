package com.crypto.funding.watchlist;

import com.crypto.funding.exchanges.ExchangeRestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Периодически обновляет funding по тем символам,
 * которые сейчас наблюдаются как funding-event candidates.
 */
@Service
public class FundingRefresherService {

    private static final Logger log = LoggerFactory.getLogger(FundingRefresherService.class);
    private final FundingWatchlistService fundingWatchlist;
    private final List<ExchangeRestClient> exchanges;

    public FundingRefresherService(
        FundingWatchlistService fundingWatchlist,
        List<ExchangeRestClient> exchanges
    ) {
        this.fundingWatchlist = fundingWatchlist;
        this.exchanges = exchanges;
    }

    @Scheduled(fixedDelayString = "#{${funding.refresh-interval-seconds:60} * 1000}")
    public void refreshFunding() {
        for (String unifiedSymbol : fundingWatchlist.symbols()) {
            for (ExchangeRestClient ex : exchanges) {
                try {
                    var info = ex.fetchFunding(unifiedSymbol);
                    if (info.nextFundingAt() == null || info.nextFundingAt().toEpochMilli() <= 0L) {
                        log.warn("[funding-refresh] missing nextFundingAt: exchange={} symbol={}", info.exchange(), unifiedSymbol);
                    }
                    if (info.secondsToFunding() <= 0L) {
                        log.warn("[funding-refresh] non-positive secondsToFunding: exchange={} symbol={} seconds={}",
                            info.exchange(), unifiedSymbol, info.secondsToFunding());
                    }
                    fundingWatchlist.updateFunding(info);
                } catch (Exception e) {
                    log.warn("[funding-refresh] failed: exchange={} symbol={}", ex.name(), unifiedSymbol, e);
                }
            }
        }
    }
}
