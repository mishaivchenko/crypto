package com.crypto.funding.engine;

import com.crypto.funding.api.ApiExceptionHandler;
import com.crypto.funding.application.query.TradeQueryService;
import com.crypto.funding.application.trade.TradeJournalService;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@ConfigurationPropertiesScan(basePackages = "com.crypto.funding.config")
@EnableConfigurationProperties(EngineProperties.class)
@EntityScan(basePackages = "com.crypto.funding.infrastructure.persistence.model")
@EnableJpaRepositories(basePackages = "com.crypto.funding.infrastructure.persistence.repository")
@Import({
    ApiExceptionHandler.class,
    TradeQueryService.class,
    TradeJournalService.class,
    EnginePlanService.class,
    EngineController.class
})
public class EngineModuleConfiguration
{
}
