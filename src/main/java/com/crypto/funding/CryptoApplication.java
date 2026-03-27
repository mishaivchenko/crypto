package com.crypto.funding;

import com.crypto.funding.market.CaffeineMarketCache;
import com.crypto.funding.market.MarketCache;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@EnableFeignClients
public class CryptoApplication
{

    public static void main( String[] args )
    {
        // Register our bot
        SpringApplication.run( CryptoApplication.class, args );
    }

    @Bean
    MarketCache marketCache() { return new CaffeineMarketCache(); }
}
