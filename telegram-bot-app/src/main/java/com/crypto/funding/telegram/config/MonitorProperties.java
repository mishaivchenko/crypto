package com.crypto.funding.telegram.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "monitor")
public record MonitorProperties(
    String baseUrl,
    String operatorToken,
    String publicUrl
)
{
}
