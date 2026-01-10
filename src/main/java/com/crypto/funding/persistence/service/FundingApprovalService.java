package com.crypto.funding.persistence.service;

import com.crypto.funding.watchlist.FundingWatchlistService;
import com.crypto.funding.watchlist.FundingWatchlistService.Item;
import com.crypto.funding.watchlist.FundingWatchlistService.WatchFunding;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Single place for funding approve/unapprove rules and validations.
 * Bot/UI should call only this service.
 */
@Service
public class FundingApprovalService {

    private final ApprovedFundingStore approvedFundingStore;
    private final FundingWatchlistService fundingWatchlist;

    public FundingApprovalService(
        ApprovedFundingStore approvedFundingStore,
        FundingWatchlistService fundingWatchlist
    ) {
        this.approvedFundingStore = approvedFundingStore;
        this.fundingWatchlist = fundingWatchlist;
    }

    public void approve(String symbol, Set<String> exchanges, BigDecimal usdt) {
        Instant nextFundingAt = resolveNextFundingAtOrThrow(symbol, exchanges);
        approvedFundingStore.approve(symbol, exchanges, usdt, nextFundingAt);
    }

    public void unapprove(String symbol) {
        approvedFundingStore.unapprove(symbol);
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

        Item item = fundingWatchlist.all().stream()
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
