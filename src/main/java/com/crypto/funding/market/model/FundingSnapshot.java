package com.crypto.funding.market.model;

import java.time.Instant;

/**
 * ratePer8h — ставка фандинга, нормализованная к интервалу 8ч (доля, не %).
 */
public record FundingSnapshot(String exchange, String symbol, double ratePer8h, Instant nextFundingAt) {}
