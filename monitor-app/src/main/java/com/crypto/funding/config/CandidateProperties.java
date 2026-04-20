package com.crypto.funding.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "trading.candidates")
public class CandidateProperties
{
    private int dedupeWindowMinutes = 15;

    public int getDedupeWindowMinutes()
    {
        return dedupeWindowMinutes;
    }

    public void setDedupeWindowMinutes( int dedupeWindowMinutes )
    {
        this.dedupeWindowMinutes = dedupeWindowMinutes;
    }
}
