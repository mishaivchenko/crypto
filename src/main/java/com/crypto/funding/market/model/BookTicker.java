package com.crypto.funding.market.model;

import java.time.Instant;

public record BookTicker(String exchange, String symbol, double bid, double ask, Instant ts) {}
