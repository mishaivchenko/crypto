package com.crypto.funding.watchlist;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ApprovedWatchlistService
{

    public record ApprovedFunding(String symbolUnified, BigDecimal quantity, Instant approvedAt) {}

    private final Map<String, ApprovedFunding> approved = new ConcurrentHashMap<>();

    public void approve(String symbolUnified, BigDecimal qty) {
        approved.put(symbolUnified, new ApprovedFunding(symbolUnified, qty, Instant.now()));
    }

    public void unapprove(String symbolUnified) {
        approved.remove(symbolUnified);
    }

    public boolean isApproved(String symbolUnified) {
        return approved.containsKey(symbolUnified);
    }

    public Collection<ApprovedFunding> all() {
        return approved.values();
    }
}
