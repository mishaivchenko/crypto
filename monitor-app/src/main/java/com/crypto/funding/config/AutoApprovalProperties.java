package com.crypto.funding.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "trading.auto-approval")
public class AutoApprovalProperties
{
    private boolean enabled = false;
    private BigDecimal maxNotionalUsd = new BigDecimal( "500" );

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled( boolean enabled )
    {
        this.enabled = enabled;
    }

    public BigDecimal getMaxNotionalUsd()
    {
        return maxNotionalUsd;
    }

    public void setMaxNotionalUsd( BigDecimal maxNotionalUsd )
    {
        this.maxNotionalUsd = maxNotionalUsd;
    }
}
