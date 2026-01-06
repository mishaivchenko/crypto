package com.crypto.funding.market;

import com.crypto.funding.market.model.BookTicker;
import com.crypto.funding.market.model.FundingSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;


class MarketCacheTest
{

//    @Test
//    void storesAndReadsSnapshot()
//    {
//        var cache = new InMemoryMarketCache();
//        cache.update( new BookTicker( "binance", "BTCUSDT", 100.0, 101.0, Instant.now() ) );
//        cache.update( new FundingSnapshot( "binance", "BTCUSDT", 0.0001, Instant.now() ) );
//
//        var snap = cache.getSnapshot( "BTCUSDT" ).orElseThrow();
//        assertThat( snap.bids() ).containsEntry( "binance", 100.0 );
//        assertThat( snap.fundingPer8h() ).containsEntry( "binance", 0.0001 );
//    }
}
