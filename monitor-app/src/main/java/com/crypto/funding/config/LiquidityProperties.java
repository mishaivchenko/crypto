package com.crypto.funding.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "monitor.liquidity")
public class LiquidityProperties
{
    private BigDecimal maxSlippageBps = new BigDecimal( "50" );
    private BigDecimal safetyHaircut = new BigDecimal( "0.30" );
    private int orderBookDepth = 20;
    private long ttlMs = 60_000L;

    private BigDecimal minTradableNotional = new BigDecimal( "50" );
    private BigDecimal thinNotional = new BigDecimal( "500" );
    private BigDecimal mediumNotional = new BigDecimal( "5000" );
    private BigDecimal goodNotional = new BigDecimal( "20000" );
    private BigDecimal excellentNotional = new BigDecimal( "100000" );

    public BigDecimal getMaxSlippageBps()
    {
        return maxSlippageBps;
    }

    public void setMaxSlippageBps( BigDecimal maxSlippageBps )
    {
        this.maxSlippageBps = maxSlippageBps;
    }

    public BigDecimal getSafetyHaircut()
    {
        return safetyHaircut;
    }

    public void setSafetyHaircut( BigDecimal safetyHaircut )
    {
        this.safetyHaircut = safetyHaircut;
    }

    public int getOrderBookDepth()
    {
        return orderBookDepth;
    }

    public void setOrderBookDepth( int orderBookDepth )
    {
        this.orderBookDepth = orderBookDepth;
    }

    public long getTtlMs()
    {
        return ttlMs;
    }

    public void setTtlMs( long ttlMs )
    {
        this.ttlMs = ttlMs;
    }

    public BigDecimal getMinTradableNotional()
    {
        return minTradableNotional;
    }

    public void setMinTradableNotional( BigDecimal minTradableNotional )
    {
        this.minTradableNotional = minTradableNotional;
    }

    public BigDecimal getThinNotional()
    {
        return thinNotional;
    }

    public void setThinNotional( BigDecimal thinNotional )
    {
        this.thinNotional = thinNotional;
    }

    public BigDecimal getMediumNotional()
    {
        return mediumNotional;
    }

    public void setMediumNotional( BigDecimal mediumNotional )
    {
        this.mediumNotional = mediumNotional;
    }

    public BigDecimal getGoodNotional()
    {
        return goodNotional;
    }

    public void setGoodNotional( BigDecimal goodNotional )
    {
        this.goodNotional = goodNotional;
    }

    public BigDecimal getExcellentNotional()
    {
        return excellentNotional;
    }

    public void setExcellentNotional( BigDecimal excellentNotional )
    {
        this.excellentNotional = excellentNotional;
    }
}
