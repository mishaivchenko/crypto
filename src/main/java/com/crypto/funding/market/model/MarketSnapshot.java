package com.crypto.funding.market.model;

import java.time.Instant;
import java.util.Map;

public record MarketSnapshot(
    String symbol,
    Map<String, Double> bids, // exchange → bid
    Map<String, Double> asks, // exchange → ask
    Map<String, Double> fundingPer8h, // exchange → rate
    Instant updatedAt) {}
