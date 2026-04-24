package com.crypto.funding.domain.execution;

import com.crypto.funding.domain.trade.TradeSide;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class OrderIntentTest
{
    // REQ: ENG-CORE-001
    @Test
    void allowsValidMarketIntentWithoutLimitPrice()
    {
        assertThatCode( () -> new OrderIntent(
            TradeSide.SHORT,
            ExecutionType.MARKET,
            BigDecimal.ONE,
            null,
            null
        ) ).doesNotThrowAnyException();
    }

    // REQ: ENG-CORE-001
    @Test
    void allowsValidLimitIntentWithPositiveLimitPrice()
    {
        assertThatCode( () -> new OrderIntent(
            TradeSide.LONG,
            ExecutionType.LIMIT,
            BigDecimal.ONE,
            BigDecimal.TEN,
            null
        ) ).doesNotThrowAnyException();
    }

    // REQ: ENG-CORE-001
    @Test
    void rejectsMissingSide()
    {
        assertThatIllegalArgumentException().isThrownBy( () -> new OrderIntent(
            null,
            ExecutionType.MARKET,
            BigDecimal.ONE,
            null,
            null
        ) ).withMessage( "side must not be null" );
    }

    // REQ: ENG-CORE-001
    @Test
    void rejectsMissingExecutionType()
    {
        assertThatIllegalArgumentException().isThrownBy( () -> new OrderIntent(
            TradeSide.SHORT,
            null,
            BigDecimal.ONE,
            null,
            null
        ) ).withMessage( "executionType must not be null" );
    }

    // REQ: ENG-CORE-001
    @Test
    void rejectsNonPositiveQuantity()
    {
        assertThatIllegalArgumentException().isThrownBy( () -> new OrderIntent(
            TradeSide.SHORT,
            ExecutionType.MARKET,
            BigDecimal.ZERO,
            null,
            null
        ) ).withMessage( "quantity must be positive" );
    }

    // REQ: ENG-CORE-001
    @Test
    void rejectsLimitIntentWithoutPositiveLimitPrice()
    {
        assertThatIllegalArgumentException().isThrownBy( () -> new OrderIntent(
            TradeSide.SHORT,
            ExecutionType.LIMIT,
            BigDecimal.ONE,
            BigDecimal.ZERO,
            null
        ) ).withMessage( "limitPrice must be positive for LIMIT intents" );
    }
}
