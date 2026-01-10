package com.crypto.funding.exchanges;

import java.util.Locale;

/**
 * Canonical exchange ids used across UI/storage.
 */
public enum Exchange {
    BINANCE("binance"),
    BYBIT("bybit"),
    GATE("gate");

    private final String id;

    Exchange(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Exchange fromId(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("exchange id must not be null");
        }
        String v = raw.trim().toLowerCase(Locale.ROOT);
        for (Exchange e : values()) {
            if (e.id.equals(v)) {
                return e;
            }
        }
        throw new IllegalArgumentException("Unsupported exchange: " + raw);
    }
}
