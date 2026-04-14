package com.crypto.funding.watchlist;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class FundingWatchlistServiceTest
{
    @Test
    void doesNotReturnFundingThatIsAlreadyInThePast()
    {
        FundingWatchlistService service = new FundingWatchlistService();

        service.addSymbol( "EDGEUSDT" );
        service.updateFunding( new FundingInfo(
            "gate",
            "EDGE/USDT",
            -0.023,
            Instant.now().minusSeconds( 300 ),
            0L,
            BigDecimal.ONE
        ) );

        assertThat( service.findFunding( "EDGE/USDT", "gate" ) ).isEmpty();
    }
}
