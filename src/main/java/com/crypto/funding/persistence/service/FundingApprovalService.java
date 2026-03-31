package com.crypto.funding.persistence.service;

import com.crypto.funding.persistence.model.ApprovedFundingEntity;
import com.crypto.funding.scheduler.FundingSchedulerService;
import com.crypto.funding.watchlist.FundingWatchlistService;
import com.crypto.funding.watchlist.FundingWatchlistService.Item;
import com.crypto.funding.watchlist.FundingWatchlistService.WatchFunding;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Legacy approval service kept for backward-compatible read/approve flows.
 * New trading development must not treat this as the target domain entry point.
 */
@Service
public class FundingApprovalService {

    private final ApprovedFundingStore store;
    private final FundingWatchlistService watchlist;
    private final FundingSchedulerService scheduler;

    public FundingApprovalService(
        ApprovedFundingStore store,
        FundingWatchlistService watchlist,
        FundingSchedulerService scheduler
    ) {
        this.store = store;
        this.watchlist = watchlist;
        this.scheduler = scheduler;
    }

    public void approve(String symbol, Set<String> exchanges, BigDecimal usdtAmount) {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(exchanges, "exchanges");
        Objects.requireNonNull(usdtAmount, "usdtAmount");

        Instant nextFundingAt = resolveNextFundingAtOrThrow(symbol, exchanges);
        store.approve(symbol, exchanges, usdtAmount, nextFundingAt);

        scheduler.wakeup("approve " + symbol);
    }

    public void unapprove(String symbol) {
        Objects.requireNonNull(symbol, "symbol");
        store.unapprove(symbol);
        scheduler.wakeup("unapprove " + symbol);
    }

    /**
     * Resolve next funding time for a given symbol, preferring selected exchanges.
     */
    public Instant resolveNextFundingAtOrThrow(String symbol, Set<String> selectedExchanges) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        if (selectedExchanges == null || selectedExchanges.isEmpty()) {
            throw new IllegalArgumentException("exchanges must not be empty");
        }

        Item item = watchlist.all().stream()
            .filter(i -> Objects.equals(i.symbol(), symbol))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Funding data not found for symbol: " + symbol));

        Map<String, WatchFunding> map = item.funding();
        if (map == null || map.isEmpty()) {
            throw new IllegalStateException("Funding data is empty for symbol: " + symbol);
        }

        // 1) Prefer selected exchanges
        for (String ex : selectedExchanges) {
            WatchFunding wf = map.get(ex);
            if (wf != null && wf.nextFundingAt() != null && wf.nextFundingAt().toEpochMilli() > 0) {
                return wf.nextFundingAt();
            }
        }

        // 2) Fallback: any valid nextFundingAt
        return map.values().stream()
            .map(WatchFunding::nextFundingAt)
            .filter(Objects::nonNull)
            .filter(t -> t.toEpochMilli() > 0)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("nextFundingAt is not available yet for symbol: " + symbol));
    }
}
