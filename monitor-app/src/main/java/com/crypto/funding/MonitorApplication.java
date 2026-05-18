package com.crypto.funding;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
    "com.crypto.funding.api",
    "com.crypto.funding.application",
    "com.crypto.funding.config",
    "com.crypto.funding.infrastructure",
    "com.crypto.funding.security"
})
@ConfigurationPropertiesScan(basePackages = "com.crypto.funding.config")
@EnableScheduling
@EnableAsync
public class MonitorApplication
{
    public static void main( String[] args )
    {
        SpringApplication.run( MonitorApplication.class, args );
    }
}
