package com.crypto.funding.domain.liquidity;

import java.math.BigDecimal;

public record OrderBookLevel(
    BigDecimal price,
    BigDecimal quantity
)
{
    public OrderBookLevel
    {
        if( price == null || price.signum() <= 0 )
        {
            throw new IllegalArgumentException( "price must be positive" );
        }
        if( quantity == null || quantity.signum() < 0 )
        {
            throw new IllegalArgumentException( "quantity must be non-negative" );
        }
    }

    public BigDecimal notional()
    {
        return price.multiply( quantity );
    }
}
