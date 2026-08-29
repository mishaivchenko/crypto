package com.crypto.funding.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "trading.auto-approval")
public class AutoApprovalProperties {
    private boolean enabled = false;
    private BigDecimal maxNotionalUsd = new BigDecimal("10");

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public BigDecimal getMaxNotionalUsd() {
        return maxNotionalUsd;
    }

    public void setMaxNotionalUsd(BigDecimal maxNotionalUsd) {
        this.maxNotionalUsd = maxNotionalUsd;
    }
}
