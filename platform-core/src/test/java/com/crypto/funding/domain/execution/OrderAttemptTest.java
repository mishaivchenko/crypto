package com.crypto.funding.domain.execution;

import com.crypto.funding.domain.trade.TradeSide;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class OrderAttemptTest
{
    // REQ: ENG-CORE-002
    @Test
    void allowsValidAttempt()
    {
        assertThatCode( OrderAttemptTest::validAttempt ).doesNotThrowAnyException();
    }

    // REQ: ENG-CORE-002
    @Test
    void allowsNullAttemptNumber()
    {
        assertThatCode( () -> new OrderAttempt(
            1L,
            "entry:5:1:2030-01-01T00:00:00Z",
            5L,
            null,
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
            null,
            null,
            "reason",
            null,
            null
        ) ).doesNotThrowAnyException();
    }

    // REQ: ENG-CORE-002
    @Test
    void rejectsMissingArmedTradeId()
    {
        assertThatIllegalArgumentException().isThrownBy( () -> new OrderAttempt(
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
            null,
            null,
            null,
            null,
            null
        ) ).withMessage( "armedTradeId must not be null" );
    }

    // REQ: ENG-CORE-002
    @Test
    void rejectsNonPositiveAttemptNumber()
    {
        assertThatIllegalArgumentException().isThrownBy( () -> new OrderAttempt(
            null,
            null,
            5L,
            0,
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
            null,
            null,
            null,
            null,
            null
        ) ).withMessage( "attemptNumber must be positive" );
    }

    // REQ: ENG-CORE-002
    @Test
    void rejectsBlankVenue()
    {
        assertThatIllegalArgumentException().isThrownBy( () -> new OrderAttempt(
            null,
            null,
            5L,
            1,
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
            null,
            null,
            null,
            null,
            null
        ) ).withMessage( "venue must not be blank" );
    }

    // REQ: ENG-CORE-002
    @Test
    void rejectsNullVenue()
    {
        assertThatIllegalArgumentException().isThrownBy( () -> new OrderAttempt(
            null,
            null,
            5L,
            1,
            null,
            "REQ/USDT",
            TradeSide.SHORT,
            ExecutionType.MARKET,
            BigDecimal.ONE,
            null,
            OrderAttemptStatus.FAILED,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        ) ).withMessage( "venue must not be blank" );
    }

    // REQ: ENG-CORE-002
    @Test
    void rejectsBlankSymbol()
    {
        assertThatIllegalArgumentException().isThrownBy( () -> new OrderAttempt(
            null,
            null,
            5L,
            1,
            "bybit",
            " ",
            TradeSide.SHORT,
            ExecutionType.MARKET,
            BigDecimal.ONE,
            null,
            OrderAttemptStatus.FAILED,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        ) ).withMessage( "symbol must not be blank" );
    }

    // REQ: ENG-CORE-002
    @Test
    void rejectsNullSymbol()
    {
        assertThatIllegalArgumentException().isThrownBy( () -> new OrderAttempt(
            null,
            null,
            5L,
            1,
            "bybit",
            null,
            TradeSide.SHORT,
            ExecutionType.MARKET,
            BigDecimal.ONE,
            null,
            OrderAttemptStatus.FAILED,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        ) ).withMessage( "symbol must not be blank" );
    }

    // REQ: ENG-CORE-002
    @Test
    void rejectsMissingSide()
    {
        assertThatIllegalArgumentException().isThrownBy( () -> new OrderAttempt(
            null,
            null,
            5L,
            1,
            "bybit",
            "REQ/USDT",
            null,
            ExecutionType.MARKET,
            BigDecimal.ONE,
            null,
            OrderAttemptStatus.FAILED,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        ) ).withMessage( "side must not be null" );
    }

    // REQ: ENG-CORE-002
    @Test
    void rejectsMissingExecutionType()
    {
        assertThatIllegalArgumentException().isThrownBy( () -> new OrderAttempt(
            null,
            null,
            5L,
            1,
            "bybit",
            "REQ/USDT",
            TradeSide.SHORT,
            null,
            BigDecimal.ONE,
            null,
            OrderAttemptStatus.FAILED,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        ) ).withMessage( "executionType must not be null" );
    }

    // REQ: ENG-CORE-002
    @Test
    void rejectsNonPositiveQuantity()
    {
        assertThatIllegalArgumentException().isThrownBy( () -> new OrderAttempt(
            null,
            null,
            5L,
            1,
            "bybit",
            "REQ/USDT",
            TradeSide.SHORT,
            ExecutionType.MARKET,
            BigDecimal.ZERO,
            null,
            OrderAttemptStatus.FAILED,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        ) ).withMessage( "quantity must be positive" );
    }

    // REQ: ENG-CORE-002
    @Test
    void rejectsNullQuantity()
    {
        assertThatIllegalArgumentException().isThrownBy( () -> new OrderAttempt(
            null,
            null,
            5L,
            1,
            "bybit",
            "REQ/USDT",
            TradeSide.SHORT,
            ExecutionType.MARKET,
            null,
            null,
            OrderAttemptStatus.FAILED,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        ) ).withMessage( "quantity must be positive" );
    }

    // REQ: ENG-CORE-002
    @Test
    void rejectsMissingStatus()
    {
        assertThatIllegalArgumentException().isThrownBy( () -> new OrderAttempt(
            null,
            null,
            5L,
            1,
            "bybit",
            "REQ/USDT",
            TradeSide.SHORT,
            ExecutionType.MARKET,
            BigDecimal.ONE,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        ) ).withMessage( "status must not be null" );
    }

    private static OrderAttempt validAttempt()
    {
        return new OrderAttempt(
            1L,
            "entry:5:1:2030-01-01T00:00:00Z",
            5L,
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
            null,
            null,
            "reason",
            null,
            null
        );
    }
}
