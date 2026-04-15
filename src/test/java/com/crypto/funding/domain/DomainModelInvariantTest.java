package com.crypto.funding.domain;

import com.crypto.funding.domain.event.FundingEvent;
import com.crypto.funding.domain.event.FundingEventStatus;
import com.crypto.funding.domain.trade.ArmedTrade;
import com.crypto.funding.domain.trade.ArmedTradeState;
import com.crypto.funding.domain.trade.TradeArmSource;
import com.crypto.funding.domain.trade.TradeSide;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainModelInvariantTest
{
    @Test
    void fundingEventRequiresVenue()
    {
        assertThatThrownBy( () -> new FundingEvent(
            null,
            " ",
            "BTC/USDT",
            Instant.now().plusSeconds( 60 ),
            null,
            FundingEventStatus.DISCOVERED,
            null,
            null,
            null,
            Instant.now(),
            null,
            null
        ) ).isInstanceOf( IllegalArgumentException.class );
    }

    @Test
    void armedTradeRejectsExitBeforeEntry()
    {
        Instant entry = Instant.now().plusSeconds( 120 );
        Instant exit = entry.minusSeconds( 30 );

        assertThatThrownBy( () -> new ArmedTrade(
            null,
            1L,
            new BigDecimal( "10" ),
            null,
            entry,
            exit,
            Instant.now(),
            1L,
            2L,
            3L,
            1,
            0L,
            null,
            null,
            null,
            TradeArmSource.EVENT_API,
            ArmedTradeState.ARMED,
            null,
            null,
            null
        ) ).isInstanceOf( IllegalArgumentException.class )
          .hasMessageContaining( "plannedExitAt" );
    }

    @Test
    void armedTradeRejectsLongSide()
    {
        Instant entry = Instant.now().plusSeconds( 120 );

        assertThatThrownBy( () -> new ArmedTrade(
            null,
            1L,
            new BigDecimal( "10" ),
            TradeSide.LONG,
            entry,
            entry.plusSeconds( 30 ),
            Instant.now(),
            1L,
            2L,
            3L,
            1,
            0L,
            null,
            null,
            null,
            TradeArmSource.EVENT_API,
            ArmedTradeState.ARMED,
            null,
            null,
            null
        ) ).isInstanceOf( IllegalArgumentException.class )
          .hasMessageContaining( "SHORT" );
    }
}
