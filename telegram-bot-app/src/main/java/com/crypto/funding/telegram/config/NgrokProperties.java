package com.crypto.funding.telegram.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ngrok")
public record NgrokProperties(
    String apiUrl,
    boolean enabled
)
{
    public NgrokProperties
    {
        if( apiUrl == null || apiUrl.isBlank() )
        {
            apiUrl = "http://localhost:4040";
        }
    }
}
