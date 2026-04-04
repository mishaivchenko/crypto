package com.crypto.funding;

import com.crypto.funding.config.CandidateProperties;
import com.crypto.funding.config.FundingCandidateSourceProperties;
import com.crypto.funding.config.MetadataSyncProperties;
import com.crypto.funding.config.TradingExecutionProperties;
import com.crypto.funding.config.VenueHttpProperties;
import com.crypto.funding.market.CaffeineMarketCache;
import com.crypto.funding.market.MarketCache;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(basePackages = "com.crypto.funding")
@ConfigurationPropertiesScan
@EnableConfigurationProperties({
    CandidateProperties.class,
    FundingCandidateSourceProperties.class,
    MetadataSyncProperties.class,
    TradingExecutionProperties.class,
    VenueHttpProperties.class
})
@EnableScheduling
@EnableFeignClients
public class CryptoApplication
{
    @Bean
    MarketCache marketCache()
    {
        return new CaffeineMarketCache();
    }
}
