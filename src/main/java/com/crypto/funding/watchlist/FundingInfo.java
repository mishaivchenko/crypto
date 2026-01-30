package com.crypto.funding.watchlist;

import java.math.BigDecimal;
import java.time.Instant;

public record FundingInfo(
    String exchange,          // "binance", "bybit", "gate"
    String symbolUnified,     // "BTC/USDT"
    double fundingRatePct,    // напр. 0.0123 -> это 0.0123%, уже умножено на 100
    Instant nextFundingAt,    // когда спишется/начислится
    long secondsToFunding,  // сколько осталось секунд до этого момента
    BigDecimal price
) {}
