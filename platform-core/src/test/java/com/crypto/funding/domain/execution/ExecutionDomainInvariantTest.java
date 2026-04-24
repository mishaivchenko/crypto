package com.crypto.funding.domain.execution;

import com.crypto.funding.domain.trade.TradeSide;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionDomainInvariantTest
{
    // REQ: ENG-CORE-001
    @Test
    void orderIntentRequiresSideExecutionTypeAndPositiveQuantity()
    {
        assertThatThrownBy( () -> new OrderIntent( null, ExecutionType.MARKET, BigDecimal.ONE, null, Instant.now() ) )
            .isInstanceOf( IllegalArgumentException.class )
            .hasMessageContaining( "side" );
        assertThatThrownBy( () -> new OrderIntent( TradeSide.SHORT, null, BigDecimal.ONE, null, Instant.now() ) )
            .isInstanceOf( IllegalArgumentException.class )
            .hasMessageContaining( "executionType" );
        assertThatThrownBy( () -> new OrderIntent( TradeSide.SHORT, ExecutionType.MARKET, BigDecimal.ZERO, null, Instant.now() ) )
            .isInstanceOf( IllegalArgumentException.class )
            .hasMessageContaining( "quantity" );
    }

    // REQ: ENG-CORE-002
    @Test
    void limitOrderIntentRequiresPositiveLimitPrice()
    {
        assertThatThrownBy( () -> new OrderIntent( TradeSide.SHORT, ExecutionType.LIMIT, BigDecimal.ONE, null, Instant.now() ) )
            .isInstanceOf( IllegalArgumentException.class )
            .hasMessageContaining( "limitPrice" );
    }

    // REQ: ENG-CORE-003
    @Test
    void orderAttemptRequiresTradeIdentityVenueAndPositiveAttemptNumber()
    {
        assertThatThrownBy( () -> new OrderAttempt(
            null,
            null,
            null,
            1,
            "bybit",
            "REQ/USDT",
            TradeSide.SHORT,
            ExecutionType.MARKET,
            BigDecimal.ONE,
            null,
            OrderAttemptStatus.FAILED,
            null,
            null,
            null,
            Instant.now(),
            null,
            "failure",
            null,
            null
        ) ).isInstanceOf( IllegalArgumentException.class )
          .hasMessageContaining( "armedTradeId" );

        assertThatThrownBy( () -> new OrderAttempt(
            null,
            null,
            1L,
            0,
            " ",
            "REQ/USDT",
            TradeSide.SHORT,
            ExecutionType.MARKET,
            BigDecimal.ONE,
            null,
            OrderAttemptStatus.FAILED,
            null,
            null,
            null,
            Instant.now(),
            null,
            "failure",
            null,
            null
        ) ).isInstanceOf( IllegalArgumentException.class )
          .hasMessageContaining( "attemptNumber" );
    }
}
