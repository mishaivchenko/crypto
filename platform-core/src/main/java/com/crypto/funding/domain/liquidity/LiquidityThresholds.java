package com.crypto.funding.domain.liquidity;

import java.math.BigDecimal;

public record LiquidityThresholds(
    BigDecimal minTradableNotional,
    BigDecimal thinNotional,
    BigDecimal mediumNotional,
    BigDecimal goodNotional,
    BigDecimal excellentNotional
)
{
    public static final LiquidityThresholds DEFAULT = new LiquidityThresholds(
        new BigDecimal( "50" ),
        new BigDecimal( "500" ),
        new BigDecimal( "5000" ),
        new BigDecimal( "20000" ),
        new BigDecimal( "100000" )
    );

    public LiquidityThresholds
    {
        if( minTradableNotional == null || minTradableNotional.signum() < 0 )
        {
            throw new IllegalArgumentException( "minTradableNotional must be non-negative" );
        }
    }
}
