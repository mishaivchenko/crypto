package com.crypto.funding.watchlist;

import com.crypto.funding.utills.SymbolMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class SymbolMapperTest
{
    @Test
    void toUnifiedForms() {
        assertThat(SymbolMapper.toUnified("BTCUSDT")).isEqualTo("BTC/USDT");
        assertThat( SymbolMapper.toUnified("btc-usdt")).isEqualTo("BTC/USDT");
        assertThat(SymbolMapper.toUnified("ETH")).isEqualTo("ETH/USDT");
    }

    @Test
    void toExchangeRemovesSlash() {
        assertThat(SymbolMapper.toExchange("BTC/USDT")).isEqualTo("BTCUSDT");
    }
}
